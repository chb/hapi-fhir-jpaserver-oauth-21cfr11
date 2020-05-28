#
# Ansible Dockerfile
#
# https://github.com/dockerfile/ansible
#

# Pull base image.
# Pull base image.
FROM python

# Install Ansible.
RUN pip install ansible

# Java up in here

# Install OpenJDK-8
RUN apt-get update && \
    apt-get install -y software-properties-common && \
    apt-get install -y openjdk-11-jdk && \
    apt-get install -y ant && \
    apt-get clean;

# Fix certificate issues
RUN apt-get update && \
    apt-get install ca-certificates-java && \
    apt-get clean && \
    update-ca-certificates -f;

# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-11-openjdk-amd64/
RUN export JAVA_HOME

# Define working directory.
WORKDIR /data

COPY keycloak-provision/keycloak-playbook.yml /data/keycloak-playbook.yml

RUN ansible-playbook keycloak-playbook.yml -i @localhost

COPY keycloak-provision/provision.sh /data/provision.sh

CMD chmod a+x /data/provision.sh

WORKDIR /data/keycloak-9.0.2/bin

ENTRYPOINT ["/data/provision.sh"] 