"""
Model preparation script.
Downloads InsightFace MobileFaceNet, converts and quantizes for mobile.
"""
import onnx
import onnxruntime
from onnxruntime.quantization import quantize_dynamic, QuantType
import numpy as np
import os

MODEL_DIR = "models"
os.makedirs(MODEL_DIR, exist_ok=True)


def prepare_mobilefacenet():
    """
    Use InsightFace model zoo.
    The buffalo_sc model pack contains a mobile-optimized face recognition model.
    """
    from insightface.app import FaceAnalysis

    app = FaceAnalysis(
        name="buffalo_sc",       # Small/compact model pack
        providers=["CPUExecutionProvider"]
    )
    app.prepare(ctx_id=-1, det_size=(160, 160))

    # The recognition model is at:
    # ~/.insightface/models/buffalo_sc/w600k_mbf.onnx
    # This is MobileFaceNet trained with ArcFace on WebFace600K
    src_path = os.path.expanduser(
        "~/.insightface/models/buffalo_sc/w600k_mbf.onnx"
    )

    if not os.path.exists(src_path):
        print(f"Model not found at {src_path}")
        print("Please ensure InsightFace downloaded the buffalo_sc model pack.")
        return

    # Quantize to INT8 for mobile
    quantize_dynamic(
        src_path,
        f"{MODEL_DIR}/mobilefacenet_arcface_int8.onnx",
        weight_type=QuantType.QUInt8
    )
    print(f"Quantized model saved to {MODEL_DIR}/mobilefacenet_arcface_int8.onnx")

    # Verify
    session = onnxruntime.InferenceSession(
        f"{MODEL_DIR}/mobilefacenet_arcface_int8.onnx"
    )
    input_shape = session.get_inputs()[0].shape
    output_shape = session.get_outputs()[0].shape
    print(f"Input shape: {input_shape}")   # [1, 3, 112, 112]
    print(f"Output shape: {output_shape}") # [1, 512]

    # Test with random input
    dummy_input = np.random.randn(1, 3, 112, 112).astype(np.float32)
    input_name = session.get_inputs()[0].name
    result = session.run(None, {input_name: dummy_input})
    print(f"Test output shape: {result[0].shape}")
    print(f"Test output norm: {np.linalg.norm(result[0]):.4f}")


def prepare_liveness_model():
    """
    MiniFASNet for passive anti-spoofing.
    Source: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
    """
    print("=== Liveness Model Preparation ===")
    print("Download MiniFASNet from:")
    print("https://github.com/minivision-ai/Silent-Face-Anti-Spoofing")
    print()
    print("Convert the PyTorch checkpoint to ONNX:")
    print()
    print("  import torch")
    print("  from src.model_lib.MiniFASNet import MiniFASNetV2")
    print("  model = MiniFASNetV2(conv6_kernel=(5, 5))")
    print("  model.load_state_dict(torch.load('minifasnet.pth', map_location='cpu'))")
    print("  model.eval()")
    print("  dummy = torch.randn(1, 3, 80, 80)")
    print("  torch.onnx.export(model, dummy, 'models/minifasnet_liveness.onnx',")
    print("                    input_names=['input'], output_names=['output'],")
    print("                    dynamic_axes={'input': {0: 'batch'}, 'output': {0: 'batch'}})")
    print()
    print("Then copy to Android assets:")
    print("  cp models/minifasnet_liveness.onnx facelog-android/app/src/main/assets/models/")


def prepare_face_detector():
    """
    MediaPipe BlazeFace short-range model for face detection.
    """
    print("=== Face Detector Preparation ===")
    print("The BlazeFace model is bundled with MediaPipe Tasks.")
    print("Download from:")
    print("https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite")
    print()
    print("Then copy to Android assets:")
    print("  cp blaze_face_short_range.tflite facelog-android/app/src/main/assets/models/")


if __name__ == "__main__":
    print("=" * 60)
    print("FaceLog Model Preparation")
    print("=" * 60)
    print()

    print("Step 1: MobileFaceNet + ArcFace (Face Recognition)")
    print("-" * 40)
    try:
        prepare_mobilefacenet()
    except ImportError:
        print("InsightFace not installed. Run: pip install insightface onnx onnxruntime")
    except Exception as e:
        print(f"Error: {e}")

    print()
    print("Step 2: MiniFASNet (Liveness Detection)")
    print("-" * 40)
    prepare_liveness_model()

    print()
    print("Step 3: BlazeFace (Face Detection)")
    print("-" * 40)
    prepare_face_detector()

    print()
    print("=" * 60)
    print("Done! Copy models to:")
    print("  facelog-android/app/src/main/assets/models/")
    print("=" * 60)
