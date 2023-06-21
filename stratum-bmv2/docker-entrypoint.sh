#!/bin/bash
set -e

stratum_bmv2 -device_id=1 \
             -chassis_config_file=/config/chassis-config.txt \
             -forwarding_pipeline_configs_file=/tmp/pipe.txt \
             -persistent_config_dir=/config \
             -initial_pipeline=/root/dummy.json \
             -cpu_port=255 \
             -external_stratum_urls=0.0.0.0:50001 \
             -local_stratum_url=localhost:55555 \
             -max_num_controllers_per_node=10 \
             -write_req_log_file=/tmp/write-reqs.txt \
             -logtosyslog=false \
             -logtostderr=true &

exec "$@"
