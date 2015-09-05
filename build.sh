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

wget http://www.us.apache.org/dist/kafka/0.8.2.1/kafka_2.10-0.8.2.1.tgz -O kafka.tgz
mkdir -p kafka && tar xzf kafka.tgz -C kafka --strip-components 1
cd kafka
bin/zookeeper-server-start.sh config/zookeeper.properties &
ZOOKEEPER_PID=$!
echo ZOOKEEPER_PID=$ZOOKEEPER_PID

bin/kafka-server-start.sh config/server.properties &
KAFKA_PID=$!
echo KAFKA_PID=$KAFKA_PID
cd ..

sleep 5
kafka/bin/kafka-topics.sh --create --partitions 1 --replication-factor 1 --topic hyperbus-test --zookeeper localhost:2181

if [ -n "$publish" ] ; then
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-transport/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-model/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-standard-model/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-t-inproc/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-t-distributed-akka/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-t-kafka/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-akka/publish
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' hyperbus-cli/publish
fi

kill -s kill $KAFKA_PID
kill -s kill $ZOOKEEPER_PID