#!/usr/bin/env python3
"""
Vaultix Panorama Slicing Script
Slices a wide panoramic image (usually 4320x1920) into 4 equal-width portrait screenshots (1080x1920)
for a continuous panoramic App Store/Play Store presentation.
"""

import os
import sys

# Auto-install Pillow if not installed
try:
    from PIL import Image
except ImportError:
    print("PIL (Pillow) is not installed. Attempting to install it now...")
    import subprocess
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
        from PIL import Image
        print("Pillow installed successfully!\n")
    except Exception as e:
        print(f"Failed to auto-install Pillow: {e}")
        print("Please install it manually by running: pip install Pillow")
        sys.exit(1)


def slice_panorama(input_path, output_dir, num_slices=4):
    if not os.path.exists(input_path):
        print(f"Error: Input image file not found at: {input_path}")
        print("Please place your panoramic image at that location or provide a different path.")
        return False

    try:
        img = Image.open(input_path)
        width, height = img.size
        print(f"Loaded image: {input_path}")
        print(f"Dimensions: {width}x{height} pixels")

        if width < height:
            print("Warning: The image width is smaller than the height. Are you sure this is a panoramic landscape image?")

        slice_width = width // num_slices
        print(f"Slicing into {num_slices} images. Each slice width: {slice_width}px, height: {height}px")

        # Create output directory if it doesn't exist
        os.makedirs(output_dir, exist_ok=True)

        for i in range(num_slices):
            left = i * slice_width
            top = 0
            right = (i + 1) * slice_width
            bottom = height

            # In case of rounding issues with division, extend the last slice to the edge
            if i == num_slices - 1:
                right = width

            slice_img = img.crop((left, top, right, bottom))
            output_filename = f"screenshot_{i + 1}.png"
            output_path = os.path.join(output_dir, output_filename)
            
            slice_img.save(output_path, "PNG")
            print(f"Saved: {output_path} (Dimensions: {slice_img.size[0]}x{slice_img.size[1]})")

        print("\nSuccess! Slicing completed. You can upload these files to Google Play Store.")
        return True

    except Exception as e:
        print(f"An error occurred during slicing: {e}")
        return False


if __name__ == "__main__":
    # Define default paths relative to workspace root
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    default_input = os.path.join(base_dir, "input", "panorama_canvas.png")
    default_output = os.path.join(base_dir, "output", "store_assets")

    input_file = sys.argv[1] if len(sys.argv) > 1 else default_input
    output_folder = sys.argv[2] if len(sys.argv) > 2 else default_output

    print("=" * 60)
    print("          VAULTIX PANORAMA SCREENSHOT SLICER")
    print("=" * 60)
    print(f"Input File:  {input_file}")
    print(f"Output Folder: {output_folder}")
    print("-" * 60)

    success = slice_panorama(input_file, output_folder)
    sys.exit(0 if success else 1)
