FROM baseimage

RUN chmod +x start_connectivity.sh

ENTRYPOINT ["/app/start_connectivity.sh"]

