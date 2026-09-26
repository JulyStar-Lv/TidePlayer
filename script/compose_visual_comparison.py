#!/usr/bin/env python3

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path)
parser.add_argument("implementation", type=Path)
parser.add_argument("output_base", type=Path)
args = parser.parse_args()

source = Image.open(args.source).convert("RGB")
implementation = Image.open(args.implementation).convert("RGB")
if source.size != implementation.size:
    raise SystemExit(f"Image sizes differ: {source.size} != {implementation.size}")

width, height = source.size
comparison = Image.new("RGB", (width * 2, height), "white")
comparison.paste(source, (0, 0))
comparison.paste(implementation, (width, 0))

overlay = Image.blend(source, implementation, 0.5)
difference = ImageChops.difference(source, implementation)
enhanced_difference = difference.point(lambda value: min(255, value * 4))

args.output_base.parent.mkdir(parents=True, exist_ok=True)
comparison.save(args.output_base.with_name(args.output_base.name + "-comparison.jpg"), quality=95)
overlay.save(args.output_base.with_name(args.output_base.name + "-overlay.jpg"), quality=95)
enhanced_difference.save(args.output_base.with_name(args.output_base.name + "-diff.jpg"), quality=95)

channel_means = ImageStat.Stat(difference).mean
print(
    f"{args.output_base.name}: size={width}x{height}, "
    f"mean_rgb=({channel_means[0]:.4f}, {channel_means[1]:.4f}, {channel_means[2]:.4f}), "
    f"mean={sum(channel_means) / 3:.4f}"
)
