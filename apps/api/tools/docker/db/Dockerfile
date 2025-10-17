#
# Build pg_timeseries first
#

FROM postgres:16-bullseye AS builder

RUN apt-get update \
	&& apt-get install -y \
	autoconf \
	ca-certificates \
	clang \
	curl \
	gcc \
	git \
	libcurl4-openssl-dev \
	liblz4-dev \
	libssl-dev \
	libzstd-dev \
	make \
	pkg-config \
	postgresql-server-dev-16

WORKDIR /timeseries

# The `.tmp` file will be storing the postgis version used
RUN git clone https://github.com/postgis/docker-postgis.git && \
	echo "export POSTGIS_VERSION=`grep '^ENV POSTGIS_VERSION' docker-postgis/16-3.5/Dockerfile | awk '{print $3}'`" >> .tmp

# install all extensions
RUN git clone https://github.com/citusdata/pg_cron.git && \
	cd pg_cron && \
	make && make install && \
	cd .. && rm -rf pg_cron

RUN git clone https://github.com/pgpartman/pg_partman.git && \
	cd pg_partman && \
	make && make install && \
	cd .. && rm -rf pg_partman

RUN git clone https://github.com/hydradatabase/hydra && \
	cd hydra/columnar &&  \
	./configure && \
	make && make install && \
	cd ../.. && rm -rf hydra

RUN git clone https://github.com/tembo-io/pg_ivm.git && \
	cd pg_ivm && \
	make && make install && \
	cd .. && rm -rf pg_ivm

# NOTE: we don't want to remove this repo because later we copy a file from it.
RUN git clone https://github.com/tembo-io/pg_timeseries.git && \
	cd pg_timeseries && \
	git checkout a2164401d5e54654e0aad5f9ab6252c93421fa95 && \
	make && make install || true && \
	cd ..

# 
# The actual image
## Copy timeseries build and install postgis
# 

FROM postgres:16-bullseye

ARG CRON_DATABASE_NAME
# Ensure the argument is set
RUN test -n "$CRON_DATABASE_NAME" 

# Copy build result pg_timeseries
COPY --from=builder /usr/share/postgresql/16/extension /usr/share/postgresql/16/extension
COPY --from=builder /usr/lib/postgresql/16/lib /usr/lib/postgresql/16/lib
# This is storing the postgis version used
COPY --from=builder /timeseries/.tmp /.tmp

COPY --from=builder /timeseries/pg_timeseries/timeseries.conf .
RUN cat timeseries.conf >> /usr/share/postgresql/16/postgresql.conf.sample
RUN echo "cron.database_name = '${CRON_DATABASE_NAME}'" >> /usr/share/postgresql/16/postgresql.conf.sample && \
	cat /usr/share/postgresql/16/postgresql.conf.sample

# Build postgis
ENV POSTGIS_MAJOR=3

RUN . /.tmp && \
	rm /.tmp && \
	apt-get update \
	&& apt-cache showpkg postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR \
	&& apt-get install -y --no-install-recommends \
	# ca-certificates: for accessing remote raster files;
	#   fix: https://github.com/postgis/docker-postgis/issues/307
	ca-certificates \
	\
	postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR=$POSTGIS_VERSION \
	postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR-scripts \
	&& rm -rf /var/lib/apt/lists/*

RUN mkdir -p /docker-entrypoint-initdb.d
COPY --from=builder /timeseries/docker-postgis/16-3.5/initdb-postgis.sh /docker-entrypoint-initdb.d/10_postgis.sh
COPY --from=builder /timeseries/docker-postgis/16-3.5/update-postgis.sh /usr/local/bin