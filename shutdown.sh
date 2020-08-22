#!/bin/sh
if [ ! -f log/tpid  ]
then
  echo "log/tpid does not exist, shutdown already?"
else
  tpid=`cat log/tpid | awk '{print $1}'`
  tpid=`ps -aef | grep $tpid | awk '{print $2}' |grep $tpid`
  if [ ${tpid} ]; then
          kill  $tpid
          echo "kill $tpid done!"
  fi
fi