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

if [ -n "$publish" ] ; then
	sbt 'set every projectBuildNumber := "'${patch_version:-SNAPSHOT}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test hyperbus-transport/publish hyperbus-annotations/publish hyperbus-model/publish hyperbus/publish hyperbus-akka/publish hyperbus-t-distributed-akka/publish hyperbus-t-kafka/publish hyperbus-cli/publish
fi
