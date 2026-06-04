FROM python:3.11-slim
WORKDIR /app
COPY modbus_simulator.py .
RUN useradd -r -s /bin/false modbus
USER modbus
EXPOSE 502
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD python -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('localhost',502)); s.close()" || exit 1
CMD ["python", "modbus_simulator.py"]
