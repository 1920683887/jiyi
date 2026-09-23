#!/bin/bash

echo "正在启动极弈..."
cd "$(dirname "$0")"

if [ ! -f "target/classes/com/jiyi/Main.class" ]; then
    echo "首次运行，正在编译项目..."
    mvn clean compile
    if [ $? -ne 0 ]; then
        echo "编译失败！"
        exit 1
    fi
fi

echo "启动极弈..."
mvn javafx:run
