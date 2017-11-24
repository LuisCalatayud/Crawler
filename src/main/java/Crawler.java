/**
 * Created by LuisAlejandro on 21/11/2017.
 */
import javax.json.*;
import javax.xml.bind.DatatypeConverter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class Crawler {

    private static final String non_formatted_url = "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&start=%s&q=%s";
    private static final String api_key = "AIzaSyAEdLWQYA__9etCwS5IV62YlXU2l5P5Mh4";
    private static final String custom_search_engine_id = "004744947786991627464:1wphwrbwhmg";

    /*
     * For simplicity we will only fetch 5 pages which will give us 50 results in total as
     * the API returns 10 results per page by default.
     */
    private static final int pages = 5;

    public Crawler() {
    }

    private final List<String> getResultURLsFromGoogleSearchAPI(String queryString) throws IOException {

        List<String> results = new ArrayList<String>();

        /*
         * We will define an array of URLS in order to fetch pages of results.
         */
        URL[] apiURLS = new URL[pages];

        for(int i = 0; i < pages; i++) {
            /* Inserting parameters into Google Custom Search API URL.
             * The queryString is URLEncoded because it could contain special characters
             * such as spaces, quotes, etc.
             */
            apiURLS[i] = new URL(String.format(non_formatted_url,
                                                api_key,
                                                custom_search_engine_id,
                                                Integer.toString(i * 10 + 1), //Start page
                                                URLEncoder.encode(queryString,"UTF-8")));
        }


        //We will now fetch the results per each generated URL
        for(int i = 0; i < pages; i++) {

            //We open the URL using a HTTPUrlConnection
            HttpURLConnection connection = (HttpURLConnection) apiURLS[i].openConnection();
            // Setting a GET request to retrieve JSON data from Google Custom Search API
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            //Gathering the API results into an apiResults object that we will "parse" later on
            JsonReader jsonReader = Json.createReader(new InputStreamReader ( ( connection.getInputStream() ) ) );
            JsonObject apiResults;
            apiResults = jsonReader.readObject();

            //Getting general search information such as total of results and search time
            JsonObject searchInfo;
            searchInfo = apiResults.getJsonObject("searchInformation");

            //Getting search results
            JsonArray searchResults;
            searchResults = apiResults.getJsonArray("items");

            //We will then storage the resulting URLS into a List
            for(JsonValue result : searchResults) {
                jsonReader = Json.createReader(new StringReader(result.toString()));
                JsonObject dummyObject = jsonReader.readObject();
                results.add(dummyObject.getString("link"));
            }

            jsonReader.close();
            connection.disconnect();
        }

        return results;
    }

    private final List<String> getJavascriptLibrariesFromURL(String urlString) throws IOException {

        List<String> javascriptLibraries = new ArrayList<String>();

        /*
         * To avoid duplication, we use a Hashmap to store already visited urls. A hashmap is chosen for speed as
         * lookups are constant
         */
        Map<String, Integer> visited = new HashMap<String, Integer>();

        /*
         * Get the url
         */
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        /*
         * Now we read the stream. In this case we get the webpage source code.
         */
        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        //Regular expression
        Pattern p = Pattern.compile("src=\"(.*?.js)\"");

        String line;
        while ((line = br.readLine()) != null) {

            /*
             * We are only interested in lines containing the string '<script' as these are the ones
             * presumably containing javascript code or links to javascript files. TO - DO: Optimize this
             * because it may be many script tags in one line
             */
            if(line.contains("<script")) {

                //Extract the src part of the line
                Matcher m = p.matcher(line);

                //If regular expression is matched, we add it to the javascript libraries list
                if(m.find()) {

                    //If the url is already visited, we discard it
                    if(visited.containsKey(m.group(1))) {
                        continue;
                    }

                    //If the URL is not visited yet, we add it to the visited structure
                    visited.put(m.group(1), 1);

                    //Some valid URLS may start with //
                    if(m.group(1).startsWith("//")) {
                        javascriptLibraries.add(url.getProtocol() + ":" + m.group(1));
                        continue;
                    }

                    //If the URL doesn't start with a protocol string or www, then is a relative path of the host
                    if(!m.group(1).startsWith("www") && !m.group(1).startsWith("https") && !m.group(1).startsWith("http")) {
                        javascriptLibraries.add(url.getProtocol() + "://" + url.getHost() + m.group(1));
                    }
                    else {
                        javascriptLibraries.add(m.group(1));
                    }
                }
            }
        }

        br.close();
        connection.disconnect();

        return javascriptLibraries;
    }

    private final String calculateCheckSum(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            md.update(content);
            byte[] checksum = md.digest();

            return DatatypeConverter.printHexBinary(checksum);

        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private final byte[] readUrlContent(InputStream is) throws IOException {

        //We have to read the stream into a byte array
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] chunk = new byte[4096]; //We are going to read the stream in 4KB chunks

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while((bis.read(chunk)) > 0) {
            out.write(chunk);
        }

        byte[] content = out.toByteArray();

        bis.close();
        out.close();

        return content;
    }

    private final String getFileNameFromURLPath(String path) {

        String filename = path.substring(path.lastIndexOf("/") + 1, path.length());
        String filenameNoExtension = filename.substring(0, filename.indexOf("."));

        return filename;
    }

    private final Map<String, Map<String, Integer>> countLibraries(List<String> javascriptLibrariesUrls) throws IOException {

        /*
         * We define a Map structure to store the libraries.
         * String key: MD5 checksum of the content.
         * Map<String, Integer> value: Map to store the name of the Javascript Library (String key)
         * and the occurrences counter (Integer value). We use this structure to prevent duplication
         */
        Map<String, Map<String, Integer>> entries = new HashMap<String, Map<String, Integer>>();

        for(String jsurl : javascriptLibrariesUrls) { //We again have to iterate all the found Javascript URLs
            URL url = new URL(jsurl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            //We discard the url if we can't retrieve it
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                continue;
            }

            byte[] content = readUrlContent(connection.getInputStream());

            String checksum = calculateCheckSum(content);

            if(!entries.containsKey(checksum)) { //If it's not found, we add it
                Map<String, Integer> m = new HashMap<String, Integer>();
                m.put(getFileNameFromURLPath(url.getPath()), 1);
                entries.put(checksum, m);
            }

        }

        return entries;
    }

    public final List<Map.Entry<String, Integer>> executeSearchQuery(String queryString)
            throws IOException, NoSuchAlgorithmException {

        List<String> urls = this.getResultURLsFromGoogleSearchAPI(queryString);

        Map<String, Map<String, Integer>> intermediate = null;
        Map<String, Integer> value;
        Map<String, Map<String, Integer>> finalResults = new HashMap<String, Map<String, Integer>>();

        for(String url: urls) {
            System.out.println("Processing: " + url);

            try {
                //For concurrency we have to execute each url into a separate thread
                intermediate = countLibraries(getJavascriptLibrariesFromURL(url)); //We count libraries per each Google API Result URL
            }
            catch(IOException a) {
                System.out.println("\t Failed to process: " + a.getMessage());
            }
            catch(RuntimeException b) {
                System.out.println("\t Failed to process: " + b.getMessage());
            }

            //We have to protect the final results variable with a mutex
            for(String key : intermediate.keySet()) { //Then merge the intermediate results with final results

                if(!finalResults.containsKey(key)) {
                    finalResults.put(key, intermediate.get(key));
                }
                else {
                    value = finalResults.get(key);
                    for(String key2 : value.keySet()) {
                        value.put(key2, value.get(key2) + 1);
                    }
                    finalResults.put(key, value);
                }
            }
        }

        //Now we add the results into a List and then sort it by value
        List<Map.Entry<String, Integer>> results = new ArrayList<Map.Entry<String, Integer>>();
        for(String key : finalResults.keySet()) {
            for(String key2 : finalResults.get(key).keySet()) {
                results.add(new AbstractMap.SimpleEntry<String, Integer>(key2, finalResults.get(key).get(key2)));
            }
        }

        Collections.sort(results,
                new Comparator<Map.Entry<String, Integer>>() {
                    public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                        return o2.getValue() - o1.getValue();
                    }
                }
        );

        return results;
    }

}
