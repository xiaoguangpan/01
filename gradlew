#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass any JVM options to Gradle.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched.
if ${cygwin} ; then
    [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$GRADLE_HOME" ] && GRADLE_HOME=`cygpath --unix "$GRADLE_HOME"`
fi

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done

APP_HOME=`dirname "$PRG"`

# Absolutize APP_HOME
# This is all we need to do to support symlinks if we execute this script.
APP_HOME=`cd "$APP_HOME" > /dev/null; pwd`

# Add a simple mechanism to note if and where the gradlew script has been sourced.
if [ -z "${GRADLEW_SOURCED-}" ]; then
  export GRADLEW_SOURCED=1
  export GRADLEW_SOURCED_SCRIPT="$APP_HOME/$APP_BASE_NAME"
fi

# Set GRADLE_OPTS from gradlew.properties
if [ -f "$APP_HOME/gradle/wrapper/gradlew.properties" ]; then
    . "$APP_HOME/gradle/wrapper/gradlew.properties"
fi

# For Cygwin, switch paths to Windows format before running java
if ${cygwin} ; then
    APP_HOME=`cygpath --path --windows "$APP_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
    CYGWIN_OPTS=`cygpath --path --windows "$CYGWIN_OPTS"`
fi

# Split up the JVM options in GRADLE_OPTS
GRADLE_OPTS_ARRAY=()
for token in $GRADLE_OPTS; do
    GRADLE_OPTS_ARRAY[${#GRADLE_OPTS_ARRAY[*]}]="$token"
done

# Split up the JVM options in JAVA_OPTS
JAVA_OPTS_ARRAY=()
for token in $JAVA_OPTS; do
    JAVA_OPTS_ARRAY[${#JAVA_OPTS_ARRAY[*]}]="$token"
done

# Collect all arguments for the java command, following the shell quoting and substitution rules
#
# (This is a shrunk down version of https://github.com/gradle/gradle/blob/v5.4.1/gradle/scripts/gradle.sh#L152-L196)
#
# It is important to note that in the original script, the quoting behavior of arguments is preserved by the shell,
# which is not the case here. So we are trying to achieve the same behavior by prefixing each argument with a
# single space and then remove the leading space in the resulting string. This is not completely equivalent to the
# original script, but it is good enough for our purpose.
#
# It is tested to work with the following shells: bash, zsh, ksh, dash, and should work with other POSIX-compliant
# shells.
#
# See https://github.com/gradle/gradle-completion/issues/104 for more details.
#
# @param ... all arguments to be passed to the java command
# @return the collected arguments in the `args` variable
collect_args() {
    args=
    for i in "$@"; do
        args="$args \"$i\""
    done
    args=${args# }
}

# Discover the location of the gradle-wrapper.jar
if [ -n "$GRADLE_WRAPPER_JAR" ]; then
    # if GRADLE_WRAPPER_JAR is specified, use it
    :
elif [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    # otherwise, use the default gradle-wrapper.jar
    GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
else
    die "Could not locate gradle-wrapper.jar. Looking for it at $APP_HOME/gradle/wrapper/gradle-wrapper.jar"
fi

# Discover the location of the JAVA_HOME.
# If we found a JAVA_HOME then we will run java. If not, we will try to run java from the path.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum number of open files
if [ "$MAX_FD" != "0" ] ; then
    # Use the maximum available, or set MAX_FD != -1 to use that value.
    if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
        # Increase the maximum number of open files to the maximum available.
        ulimit -n -S `ulimit -H -n` > /dev/null 2>&1 || \
            warn "Could not set maximum file descriptor limit: $?"
    else
        ulimit -n -S "$MAX_FD" > /dev/null 2>&1 || \
            warn "Could not set file descriptor limit to $MAX_FD: $?"
    fi
fi

# Collect all arguments for the java command.
collect_args "${DEFAULT_JVM_OPTS[@]}" "${JAVA_OPTS_ARRAY[@]}" "${GRADLE_OPTS_ARRAY[@]}" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$GRADLE_WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"

# Start the Gradle main class.
#
# Note: We are using eval to properly handle the quoting of arguments.
eval exec \"$JAVACMD\" "$args"
