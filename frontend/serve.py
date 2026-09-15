"""
[담당: 양혜승] 프론트엔드 로컬 개발 서버
python -m http.server는 캐시 비활성화 헤더를 보내지 않아, 브라우저가 JS/CSS를
디스크 캐시에 오래 붙들고 있어 변경사항이 반영 안 되는 것처럼 보이는 문제가 있다.
이 스크립트는 모든 응답에 Cache-Control: no-store를 붙여 그 문제를 없앤다.

사용법:
    cd frontend
    python serve.py            # 기본 포트 3000
    python serve.py 3001       # 포트 지정
"""
import http.server
import sys
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
ROOT = Path(__file__).resolve().parent / "src"


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        super().end_headers()


if __name__ == "__main__":
    with http.server.ThreadingHTTPServer(("", PORT), NoCacheHandler) as httpd:
        print(f"Serving {ROOT} at http://localhost:{PORT} (Cache-Control: no-store)")
        httpd.serve_forever()
