from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

import gradio as gr


def separate_audio(audio_path: str | None) -> str:
    if not audio_path:
        raise gr.Error("Please upload an audio file first.")

    source = Path(audio_path)
    if not source.is_file():
        raise gr.Error("The uploaded audio file could not be found.")

    # Keep the first free-tier test conservative: one CPU job, standard 4-stem model.
    with tempfile.TemporaryDirectory(prefix="cbt-stems-") as temp_dir:
        work = Path(temp_dir)
        input_dir = work / "input"
        output_dir = work / "output"
        input_dir.mkdir()
        output_dir.mkdir()

        safe_name = source.name or "audio"
        copied_input = input_dir / safe_name
        shutil.copy2(source, copied_input)

        command = [
            "demucs",
            "--device", "cpu",
            "--jobs", "1",
            "--shifts", "1",
            "-n", "htdemucs",
            "--mp3",
            "--mp3-bitrate", "192",
            "-o", str(output_dir),
            str(copied_input),
        ]

        try:
            result = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
                timeout=2400,
            )
        except subprocess.TimeoutExpired as exc:
            raise gr.Error(
                "Processing took too long on the free CPU server. Try a shorter audio file."
            ) from exc

        if result.returncode != 0:
            details = (result.stderr or result.stdout or "Unknown Demucs error")[-2500:]
            raise gr.Error(f"Demucs could not separate this audio:\n{details}")

        stem_dir = output_dir / "htdemucs" / copied_input.stem
        expected = ["vocals.mp3", "drums.mp3", "bass.mp3", "other.mp3"]
        missing = [name for name in expected if not (stem_dir / name).is_file()]
        if missing:
            raise gr.Error("Demucs finished, but these stem files are missing: " + ", ".join(missing))

        zip_path = work / f"{copied_input.stem}_CBT_Stems.zip"
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in expected:
                archive.write(stem_dir / name, arcname=name)

        # Gradio copies returned files to its own cache before the temp folder is removed.
        return str(zip_path)


demo = gr.Interface(
    fn=separate_audio,
    inputs=gr.Audio(
        sources=["upload"],
        type="filepath",
        label="Upload a song (start with a short test track)",
    ),
    outputs=gr.File(label="Download separated stems (ZIP)"),
    title="CBT — AI Stem Studio",
    description=(
        "Separate a song into vocals, drums, bass, and other using Demucs. "
        "Free CPU hosting may be slow and can sleep when idle."
    ),
    api_name="separate",
)

if __name__ == "__main__":
    demo.queue(default_concurrency_limit=1).launch()
