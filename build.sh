#!/bin/bash -e

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
	sbt 'set every projectBuildNumber := "'${patch_version:-0}'"' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test servicebus/publish hyperbus-inner/publish hyperbus/publish servicebus-t-distributed-akka/publish hyperbus-cli/publish
fi
