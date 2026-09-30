"""Simple APK download server."""
import http.server
import os
import socket

APK_PATH = "/Users/pankaj/Desktop/FaceLog/facelog-android/app/build/outputs/apk/debug/app-debug.apk"
PORT = 8080

HTML = """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>FaceLog APK Download</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, sans-serif; background: #0f172a; color: #e2e8f0; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
        .card { background: #1e293b; border-radius: 16px; padding: 40px; text-align: center; max-width: 400px; width: 90%; }
        h1 { font-size: 24px; margin-bottom: 8px; color: #fff; }
        .version { color: #94a3b8; margin-bottom: 24px; }
        .size { color: #64748b; font-size: 14px; margin-bottom: 24px; }
        .btn { display: inline-block; background: #3b82f6; color: #fff; text-decoration: none; padding: 14px 32px; border-radius: 10px; font-size: 18px; font-weight: 600; }
        .btn:active { background: #2563eb; }
        .note { color: #64748b; font-size: 12px; margin-top: 20px; line-height: 1.5; }
    </style>
</head>
<body>
    <div class="card">
        <h1>FaceLog</h1>
        <p class="version">Debug Build v1.0.0</p>
        <p class="size">APK_SIZE</p>
        <a class="btn" href="/download">Download APK</a>
        <p class="note">On Android: Allow installation from unknown sources when prompted.</p>
    </div>
</body>
</html>"""


class APKHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/download":
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            size = os.path.getsize(APK_PATH)
            self.send_header("Content-Length", str(size))
            self.send_header("Content-Disposition", 'attachment; filename="FaceLog-debug.apk"')
            self.end_headers()
            with open(APK_PATH, "rb") as f:
                while chunk := f.read(65536):
                    self.wfile.write(chunk)
        else:
            size_mb = os.path.getsize(APK_PATH) / (1024 * 1024)
            page = HTML.replace("APK_SIZE", f"{size_mb:.1f} MB")
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(page.encode())

    def log_message(self, format, *args):
        print(f"[APK Server] {args[0]}")


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "localhost"


if __name__ == "__main__":
    ip = get_local_ip()
    server = http.server.HTTPServer(("0.0.0.0", PORT), APKHandler)
    print(f"\n  FaceLog APK Download Server")
    print(f"  ===========================")
    print(f"  Local:   http://localhost:{PORT}")
    print(f"  Network: http://{ip}:{PORT}")
    print(f"\n  Open the Network URL on your phone to download.\n")
    server.serve_forever()
