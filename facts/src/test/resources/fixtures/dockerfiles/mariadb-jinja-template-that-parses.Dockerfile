FROM {{ spec.s2i_base }}:{{ spec.s2i_base_tag }}

# MariaDB image for OpenShift.
#
# Volumes:
#  * /var/lib/mysql/data - Datastore for MariaDB
# Environment:
#  * $MYSQL_USER - Database user name
#  * $MYSQL_PASSWORD - User's password
#  * $MYSQL_DATABASE - Name of the database to create
#  * $MYSQL_ROOT_PASSWORD (Optional) - Password for the 'root' MySQL account

# Standalone ENV call so these values can be re-used in the other ENV calls
ENV MYSQL_VERSION={{ spec.version }} \
    MYSQL_SHORT_VERSION={{ spec.short }}
