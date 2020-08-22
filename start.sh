#!/bin/sh
if [ ! -d log  ]
then
mkdir -p log
fi
rm -f log/tpid
nohup java -jar fileProxy-1.0.0-all.jar > /dev/null 2>&1 &
echo $! > log/tpid

pid=`cat log/tpid | awk '{print $1}'`
pid=`ps -aef | grep $pid | awk '{print $2}' |grep $pid`
if [ ${pid} ]; then
        echo "start successfully, pid=$pid"
else
        echo "fail to start!"
fi
