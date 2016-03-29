#!/bin/bash -e
set -x

for param in "$@"
	do case $param in
		--publish*)
			publish="1"
		;;
		--patch-version=*)
			patch_version="${param#*=}"
		;;
	esac
done


ZOOKEEPER_PEERS=localhost:2181
KAFKA_PEERS=localhost:9092

wget http://www-eu.apache.org/dist/kafka/0.8.2.2/kafka_2.9.1-0.8.2.2.tgz -O kafka.tgz
mkdir -p kafka && tar xzf kafka.tgz -C kafka --strip-components 1
kafka/bin/zookeeper-server-start.sh kafka/config/zookeeper.properties &
ZOOKEEPER_PID=$!

kafka/bin/kafka-server-start.sh kafka/config/server.properties &
KAFKA_PID=$!

sleep 5

kafka/bin/kafka-topics.sh --create --partitions 1 --replication-factor 1 --topic hyperbus-test --zookeeper localhost:2181

SBT_RESULT=0

set +e

if [ -n "$publish" ] ; then
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test publish
	SBT_RESULT=$?
fi

kill $KAFKA_PID
sleep 5
kill $ZOOKEEPER_PID

exit $SBT_RESULT
