# fusion-logs-demo
Index and search request logs with Fusion.

Custom log crawling connector parses log files and emits documents containing entire request log sequence from start to end which are then fed into Fusion pipeline for indexing and after that are ready to be searched.

#### Installing custom log connector
Clone this repository and invoke `install` gradle task in log-connector dir: 
```
git clone https://github.com/objectstyle/fusion-logs-demo.git
cd fusion-logs-demo/log-connector
./gradlew install -PfusionHome=/path/to/local/fusion/installation
 ```
After the installation is complete restart Fusion.
