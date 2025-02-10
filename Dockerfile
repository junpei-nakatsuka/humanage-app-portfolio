# ベースとなるイメージを指定 (Node.jsの公式イメージを使用)
FROM node:20

# 必要なパッケージや依存関係をインストール
RUN set -e; apt-get update && apt-get install -y \
  curl \
  gnupg \
  lsb-release \
  fonts-liberation \
  fonts-noto-cjk \
  chromium \
  libnss3 \
  libappindicator3-1 \
  libasound2 \
  libatk-bridge2.0-0 \
  libatk1.0-0 \
  libcups2 \
  libx11-xcb1 \
  libxcomposite1 \
  libxdamage1 \
  libxrandr2 \
  libgbm1 \
  libpango-1.0.0 \
  libgtk-3.0 \
  xdg-utils \
  supervisor \
  openjdk-17-jdk-headless \
  strace \
  gdb \
  vim \
  --no-install-recommends \
  && rm -rf /var/lib/apt/lists/*

# Puppeteerが使うブラウザのパスを設定 (Chromiumを使用)
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium
ENV PATH="/usr/bin/chromium:${PATH}"
ENV CHROME_BIN=/usr/bin/chromium

# 作業ディレクトリを指定
WORKDIR /app

# アプリケーションコードをコピー
COPY . .

# 依存関係のインストール
RUN npm install

# supervisor設定ファイルをコピー
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Webプロセス用のポートを指定（Herokuが割り当てるポート）
EXPOSE 8080

# supervisorを起動
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
