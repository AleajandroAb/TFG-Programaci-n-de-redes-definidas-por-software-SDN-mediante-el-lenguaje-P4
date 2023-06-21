FROM opennetworking/mn-stratum

RUN dockerd --privileged &

RUN mkdir -p /var/log/stratum

RUN mkdir -p /config
VOLUME /config

COPY chassis-config.txt /config/.
RUN chmod 777 /config/chassis-config.txt

COPY ./docker-entrypoint.sh /
RUN chmod 777 /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/bin/bash"]
