package sample;

import java.io.File;
import java.util.List;
import java.util.Optional;

import fatjar.Cache;
import fatjar.DB;
import fatjar.HttpClient;
import fatjar.IO;
import fatjar.JSON;
import fatjar.Log;
import fatjar.Server;
import fatjar.XML;
import fatjar.dto.HttpMethod;
import fatjar.dto.Status;

public class Main {

    public static void main(String[] args) {
        Main tester = new Main();
        tester.exampleServer();
    }

    private int getAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 8080;
    }

    private void exampleServer() {
        // do a request to http://localhost:80/ and/or http://localhost:80/Hi
        Server.create().
                listen(this.getAssignedPort(), "0.0.0.0")
                .register(Status.STATUS_BAD_REQUEST, (req, res) -> {
                    res.setContentType("text/html");
                    res.setContent("<h1>BAD REQUEST!</h1>");
                    res.write();
                })
                .filter("/*", (req, res) -> {
                    Log.info("Wildcard filter called");
                })
                .filter("/Hi", (req, res) -> {
                    Log.info("/Hi filter called");
                })
                .get("/", (req, res) -> {
                    res.setContent("Welcome");
                    res.write();
                })
                .get("/Hi", (req, res) -> {
                    if (req.getParams().containsKey("name")) {
                        res.setContent("Hello " + req.getParams().getValue("name"));
                    } else {
                        res.setContent("type \"http://localhost:80/Hi?name=john\" in your browser");
                    }
                    res.write();
                })
                .get("/cache", (req, res) -> {
                    Cache<String, Object> cache = Cache.create("FatJarLocalCache");
                    cache.put("key", "value");
                    if (cache.get("number") == null) {
                        cache.put("number", 1);
                    } else {
                        cache.put("number", (Integer) cache.get("number") + 1);
                    }
                    res.setContent(JSON.toJson(cache.getAll()));
                    res.write();
                })
                .get("/file", (request, response) -> {
                    String path = File.separator + "tmp";
                    String fileName = "file.temp";
                    String fileContent = "file content here!";
                    boolean result = IO.writeFile(path, fileName, fileContent);
                    if (result) {
                        Optional<String> content = IO.readFile(path, fileName);
                        if (content.isPresent()) {
                            response.setContent(content.get());
                            response.write();
                        } else {
                            throw new Server.ServerException(Status.STATUS_NOT_FOUND);
                        }
                    } else {
                        throw new Server.ServerException(Status.STATUS_INTERNAL_SERVER_ERROR, "could not create file");
                    }
                })
                .get("/db", (req, res) -> {

                    Optional<DB> dbOptional = DB.create();
                    if (dbOptional.isPresent()) {
                        DB db = dbOptional.get();
                        long numberOfAllEntities = db.count(MyEntity.class);
                        long numberOfJohnEntities = db.count(MyEntity.class, DB.Query.create("name", "five"));
                        long numberOfLargeIDEntities = db.count(MyEntity.class, DB.Query.create("id", DB.Sign.GT, 1L));
                        Log.info("numberOfLargeIDEntities: " + numberOfLargeIDEntities);

                        List<MyEntity> allEntities = db.findAll(MyEntity.class);
                        Log.info("found allEntities: " + allEntities);

                        List<MyEntity> entityNameFive = db.find(MyEntity.class, DB.Query.create("name", "five"));
                        if (!entityNameFive.isEmpty()) {
                            Log.info("fount entityNameFive: " + entityNameFive);
                        }

                        List<MyEntity> entityNameFourOrFive = db.find(MyEntity.class, DB.Query.or(
                                DB.Query.create("name", "four"),
                                DB.Query.create("name", "five")
                        ));
                        if (!entityNameFourOrFive.isEmpty()) {
                            Log.info("fount entityNameFourOrFive: " + entityNameFourOrFive);
                        }

                        List<MyEntity> entityNameID5AndNameFive = db.find(MyEntity.class, DB.Query.and(
                                DB.Query.create("id", 5),
                                DB.Query.create("name", "five")
                        ));
                        if (!entityNameID5AndNameFive.isEmpty()) {
                            Log.info("fount entityNameID5AndNameFive: " + entityNameID5AndNameFive);
                        }

                        MyEntity entityID1L = db.find(MyEntity.class, 1L);
                        Log.info("fount entityID1L: " + entityID1L);

                        MyEntity entity = new MyEntity("john");
                        Optional<MyEntity> insertedEntityOptional = db.insert(entity);
                        if (insertedEntityOptional.isPresent()) {
                            MyEntity insertedEntity = insertedEntityOptional.get();
                            Log.info("insert insertedEntity: " + insertedEntity);

                            insertedEntity.setName("johnny");
                            db.update(insertedEntity);
                            Log.info("update insertedEntity: " + insertedEntity);

                            db.delete(insertedEntity);
                            Log.info("delete insertedEntity: " + insertedEntity);
                            res.setContent(JSON.toJson(insertedEntity));
                        } else {
                            res.setContent("could not insert to DB");
                        }
                        res.write();
                    } else {
                        throw new Server.ServerException("no db available");
                    }
                })
                .get("/httpClient", (req, res) -> {
                    try {
                        String content = HttpClient.create().url("http://ip.jsontest.com/").method(HttpMethod.GET).send().getContentAsString();
                        res.setContent("got content: " + content);
                    } catch (HttpClient.HttpClientException e) {
                        res.setContent("got exception: " + e);
                    }
                    res.write();
                })
                .get("/badgeFlat", (req, res) -> {
                    byte[] content = IO.readBinaryFile("target/classes", "heroku-badge-deployed.png").get();
                    res.setContentType("image/png");
                    res.setContentChar(content);
                    res.write();
                })
                .get("/throwException", (req, res) -> {
                    throw new Server.ServerException("tojsonexception");
                })
                .get("/badRequest", (req, res) -> {
                    throw new Server.ServerException(Status.STATUS_BAD_REQUEST);
                })
                .get("/toJSON", (req, res) -> {
                    res.setContent(JSON.toJson(new MyPOJO(req.getParam("name", "add a query parameter like '?name=john'"), 101)));
                    res.write();
                })
                .post("/fromJSON", (req, res) -> {
                    MyPOJO myPOJO = JSON.fromJson(new String(req.getBody()), MyPOJO.class);
                    res.setContent(JSON.toJson(myPOJO));
                    res.write();
                }).get("/toXML", (req, res) -> {
					res.setContent(XML.toXML(new MyPOJO(req.getParam("name", "add a query parameter like '?name=doe'"), Integer.MAX_VALUE)));
					res.write();
				}).post("/fromXML", (req, res) -> {
					MyPOJO myPOJO = XML.fromXML(req.getBody(), MyPOJO.class);
					res.setContent(XML.toXML(myPOJO));
					res.write();
				}).post("/", (req, res) -> {
                })
                .delete("/", (req, res) -> {
                })
                .put("/", (req, res) -> {
                })
                .start();
    }
}
