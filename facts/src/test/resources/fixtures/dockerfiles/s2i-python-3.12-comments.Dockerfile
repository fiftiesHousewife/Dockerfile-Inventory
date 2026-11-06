FROM quay.io/sclorg/s2i-base-c10s:c10s
# Python 3.7+ only
# Yes, the directory below is already copied by the previous command.
# The problem here is that the wheels directory is copied as a symlink.
# Only if you specify symlink directly as a source, COPY copies all the
# files from the symlink destination.
COPY 3.12/root/opt/wheels /opt/wheels
# - Create a Python virtual environment for use by any application to avoid
#   potential conflicts with Python packages preinstalled in the main Python
#   installation.
# - In order to drop the root user, we have to make some directories world
#   writable as OpenShift default security model is to run the container
#   under random UID.
RUN python3.12 -m venv ${APP_ROOT} && \
# Python 3.7+ only code, Python <3.7 installs pip from PyPI in the assemble script. \
# We have to upgrade pip to a newer verison because \
# pip < 19.3 does not support manylinux2014 wheels. Only manylinux2014 (and later) wheels \
#   support platforms like ppc64le, aarch64 or armv7 \
# We are newly using wheel from one of the latest stable Fedora releases (from RPM python-pip-wheel) \
# because it's tested better then whatever version from PyPI and contains useful patches. \
# We have to do it here (in the macro) so the permissions are correctly fixed and pip is able \
# to reinstall itself in the next build phases in the assemble script if user wants the latest version \
${APP_ROOT}/bin/pip install /opt/wheels/pip-* && \
rm -r /opt/wheels && \
chown -R 1001:0 ${APP_ROOT} && \
fix-permissions ${APP_ROOT} -P && \
# The following echo adds the unset command for the variables set below to the \
# venv activation script. This prevents the virtual environment from being \
# activated multiple times and also every time the prompt is rendered. \
echo "unset BASH_ENV PROMPT_COMMAND ENV" >> ${APP_ROOT}/bin/activate
