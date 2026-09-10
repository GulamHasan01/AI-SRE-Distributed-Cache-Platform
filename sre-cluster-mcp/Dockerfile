FROM python:3.12-slim

WORKDIR /app

# Install system deps for docker SDK
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p /app/audit

# Default: SSE transport for use with Docker Compose
# Override CMD for STDIO when using with local agent process
CMD ["python", "server.py", "--transport", "sse", "--port", "8000"]
