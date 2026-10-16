#!/usr/bin/env python3
"""Encode a verified gameplay capture; no desktop capture, music or external assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path


def ass_time(ticks: int) -> str:
    centiseconds = ticks * 5
    return f"{centiseconds // 360000}:{centiseconds // 6000 % 60:02}:{centiseconds // 100 % 60:02}.{centiseconds % 100:02}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    args = parser.parse_args()
    root = args.capture.resolve()
    receipt = json.loads((root / "client-receipt.json").read_text(encoding="utf-8-sig"))
    if not receipt["allScenariosPassed"] or receipt["scenarioCount"] != 9 or receipt["cleanupFailureCount"]:
        raise ValueError("A complete, successful nine-scene capture is required")
    if receipt["desktopIsolation"]["violationCount"] or not receipt["desktopIsolation"]["silentDevice"]:
        raise ValueError("Desktop isolation did not pass")
    frames = json.loads((root / "capture-manifest.json").read_text(encoding="utf-8-sig"))
    if len(frames) < 300:
        raise ValueError("Not enough captured frames for a gameplay video")
    concat = ["ffconcat version 1.0"]
    last_tick = 0
    total_ticks = receipt["scenarios"][-1]["finishedAtTick"]
    for index, frame in enumerate(frames):
        name = frame["path"]
        if Path(name).name != name or not name.startswith("frame-") or not name.endswith(".png"):
            raise ValueError("Unexpected frame path")
        data = (root / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != frame["sha256"] or frame["tick"] <= last_tick:
            raise ValueError("Frame changed or capture order is invalid")
        next_tick = frames[index + 1]["tick"] if index + 1 < len(frames) else total_ticks + 1
        concat += [f"file '{name}'", f"duration {(next_tick - frame['tick']) / 20:.6f}"]
        last_tick = frame["tick"]
    concat.append(f"file '{frames[-1]['path']}'")
    (root / "showcase.ffconcat").write_text("\n".join(concat) + "\n", encoding="utf-8")
    captions = [
        "[Script Info]", "ScriptType: v4.00+", "PlayResX: 1280", "PlayResY: 720", "WrapStyle: 0", "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        "Style: Title,Segoe UI,25,&H00FFFFFF,&H00FFFFFF,&H00202020,&H80000000,-1,0,0,0,100,100,0,0,1,1,0,7,24,24,18,1",
        "Style: Caption,Segoe UI,29,&H00FFFFFF,&H00FFFFFF,&H001A1A1A,&H80000000,0,0,0,0,100,100,0,0,3,2,0,2,36,36,34,1", "",
        "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        f"Dialogue: 0,0:00:00.00,{ass_time(total_ticks)},Title,,0,0,0,,Companion Dog Framework  |  Minecraft 1.21.1",
    ]
    for scene in receipt["scenarios"]:
        if not scene["passed"] or not scene["cleanupPassed"]:
            raise ValueError("Scene observation or cleanup failed")
        caption = "Give paw - a playful greeting" if scene["id"] == "give-paw" else scene["caption"]
        captions.append(f"Dialogue: 0,{ass_time(scene['startedAtTick'])},{ass_time(scene['finishedAtTick'])},Caption,,0,0,0,,{caption}")
    (root / "showcase.ass").write_text("\n".join(captions) + "\n", encoding="utf-8")
    video = root / "companiondog-gameplay-showcase.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "warning", "-y", "-f", "concat", "-safe", "1",
                    "-i", "showcase.ffconcat", "-vf", "ass=showcase.ass", "-t", str(total_ticks / 20),
                    "-r", "30", "-fps_mode", "cfr", "-c:v", "libx264", "-preset", "fast", "-crf", "21",
                    "-pix_fmt", "yuv420p", "-threads", "2", "-movflags", "+faststart", "-an",
                    "-metadata", "title=Companion Dog Framework - Gameplay Showcase",
                    "-metadata", "artist=Alex Chengyu Li", "-metadata", "comment=CC BY 4.0; third-party rights retained",
                    video.name], cwd=root, check=True)
    probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(video)], text=True))
    streams = probe["streams"]
    if len(streams) != 1 or streams[0]["codec_name"] != "h264" or streams[0]["pix_fmt"] != "yuv420p":
        raise ValueError("Video encoding is not the expected silent H.264 format")
    if streams[0]["width"] != 1280 or streams[0]["height"] != 720:
        raise ValueError("Video resolution mismatch")
    if abs(float(probe["format"]["duration"]) - total_ticks / 20) > 0.1:
        raise ValueError("Video duration mismatch")
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", "15.5", "-i", str(video),
                    "-frames:v", "1", "-threads", "1", "companion-gameplay-poster.png"], cwd=root, check=True)
    result = {"status": "encoded-from-verified-gameplay-frames", "duration_seconds": total_ticks / 20,
              "capture_frame_count": len(frames), "scenes": len(receipt["scenarios"]),
              "video": {"filename": video.name, "bytes": video.stat().st_size,
                        "sha256": hashlib.sha256(video.read_bytes()).hexdigest()}, "probe": probe,
              "capture_receipt_sha256": hashlib.sha256((root / "client-receipt.json").read_bytes()).hexdigest()}
    (root / "video-verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "duration_seconds", "capture_frame_count", "scenes", "video")}))


if __name__ == "__main__":
    main()
