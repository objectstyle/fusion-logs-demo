# fusion-logs-demo
Index and search request logs with Fusion.

Custom log crawling connector parses log files and emits documents containing entire request log sequence from start to end which are then fed into Fusion pipeline for indexing and after that are ready to be searched.

#### Running this demo
1. Clone this repository and invoke `install` gradle task in log-connector dir: 

 ```
git clone https://github.com/objectstyle/fusion-logs-demo.git
cd fusion-logs-demo/log-connector
./gradlew install -PfusionHome=/path/to/local/fusion/installation
 ```
2. Restart Fusion;
3. Import `fusion-logs-demo.json.postman_collection` into Postman and run the requests;
4. Navigate to the `logs-demo` collection in Fusion ([http://localhost:8764/panels/logs-demo](http://localhost:8764/panels/logs-demo)) and try searching.
