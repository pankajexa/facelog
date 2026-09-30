"""
Fine-tune MobileFaceNet on Indian face dataset.
Uses ArcFace loss, trains for 10-20 epochs on curated Indian dataset.
"""
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
import os


class ArcFaceLoss(nn.Module):
    def __init__(self, num_classes, embedding_dim=512, margin=0.5, scale=64):
        super().__init__()
        self.weight = nn.Parameter(torch.FloatTensor(num_classes, embedding_dim))
        nn.init.xavier_uniform_(self.weight)
        self.margin = margin
        self.scale = scale
        self.cos_m = torch.cos(torch.tensor(margin))
        self.sin_m = torch.sin(torch.tensor(margin))
        self.threshold = torch.cos(torch.tensor(torch.pi - margin))
        self.mm = torch.sin(torch.tensor(torch.pi - margin)) * margin

    def forward(self, embeddings, labels):
        cosine = torch.nn.functional.linear(
            torch.nn.functional.normalize(embeddings),
            torch.nn.functional.normalize(self.weight)
        )
        sine = torch.sqrt(1.0 - torch.pow(cosine, 2))
        phi = cosine * self.cos_m - sine * self.sin_m

        phi = torch.where(cosine > self.threshold, phi, cosine - self.mm)

        one_hot = torch.zeros(cosine.size(), device=embeddings.device)
        one_hot.scatter_(1, labels.view(-1, 1).long(), 1)

        output = (one_hot * phi) + ((1.0 - one_hot) * cosine)
        output *= self.scale
        return output


class FaceDataset(Dataset):
    """Dataset for face images organized as: data_dir/identity_id/image.jpg"""

    def __init__(self, data_dir, transform=None):
        self.data_dir = data_dir
        self.transform = transform or transforms.Compose([
            transforms.Resize((112, 112)),
            transforms.RandomHorizontalFlip(),
            transforms.ColorJitter(brightness=0.2, contrast=0.2),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5]),
        ])

        self.samples = []
        self.class_to_idx = {}

        if os.path.exists(data_dir):
            for idx, identity in enumerate(sorted(os.listdir(data_dir))):
                identity_dir = os.path.join(data_dir, identity)
                if not os.path.isdir(identity_dir):
                    continue
                self.class_to_idx[identity] = idx
                for img_name in os.listdir(identity_dir):
                    if img_name.lower().endswith(('.jpg', '.jpeg', '.png')):
                        self.samples.append((
                            os.path.join(identity_dir, img_name),
                            idx
                        ))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        from PIL import Image
        img_path, label = self.samples[idx]
        image = Image.open(img_path).convert('RGB')
        if self.transform:
            image = self.transform(image)
        return image, label


def finetune(
    data_dir: str,
    model_path: str = "models/mobilefacenet_arcface_int8.onnx",
    output_path: str = "models/mobilefacenet_arcface_indian.onnx",
    epochs: int = 15,
    lr: float = 1e-4,
    batch_size: int = 64,
):
    """
    Fine-tuning procedure:
    1. Collect 500+ Indian identities with 10+ images each
    2. Preprocess: align, crop to 112x112
    3. Load pre-trained MobileFaceNet backbone (freeze first 80% of layers)
    4. Train with ArcFace loss for 15-20 epochs, lr=1e-4, cosine decay
    5. Validate on held-out Indian face pairs
    6. Export to ONNX, quantize to INT8
    """
    print(f"Loading dataset from {data_dir}...")
    dataset = FaceDataset(data_dir)
    if len(dataset) == 0:
        print("No data found. Please prepare the dataset first.")
        print("Expected structure: data_dir/identity_id/image.jpg")
        return

    num_classes = len(dataset.class_to_idx)
    print(f"Found {len(dataset)} images across {num_classes} identities")

    dataloader = DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=True,
        num_workers=4,
        pin_memory=True
    )

    # Load model (placeholder - in practice, load MobileFaceNet PyTorch weights)
    print("Note: This script requires a PyTorch MobileFaceNet checkpoint.")
    print("Convert from ONNX or use the InsightFace PyTorch implementation.")
    print()
    print("Example training loop would be:")
    print(f"  - {epochs} epochs")
    print(f"  - Learning rate: {lr}")
    print(f"  - Batch size: {batch_size}")
    print(f"  - ArcFace margin: 0.5, scale: 64")
    print(f"  - Optimizer: AdamW with cosine annealing")
    print(f"  - Output: {output_path}")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fine-tune MobileFaceNet on Indian faces")
    parser.add_argument("--data-dir", type=str, default="data/indian_faces",
                        help="Path to Indian face dataset")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--batch-size", type=int, default=64)
    args = parser.parse_args()

    finetune(
        data_dir=args.data_dir,
        epochs=args.epochs,
        lr=args.lr,
        batch_size=args.batch_size,
    )
