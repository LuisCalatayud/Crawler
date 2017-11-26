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

    private static final List<String> getResultURLsFromGoogleSearchAPI(String queryString) throws IOException {

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

    private static final List<String> getJavascriptLibrariesFromURL(String urlString) throws IOException {

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

    private static final String calculateCheckSum(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            md.update(content);
            byte[] checksum = md.digest();

            /*
            * We convert te digest message into a legible Hex String.
            * This is marked as deprecated in Java 9.
            * TO - DO: find another solution
            */
            return DatatypeConverter.printHexBinary(checksum);

        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static final byte[] readUrlContent(InputStream is) throws IOException {

        //We have to read the stream into a byte array
        byte[] chunk = new byte[4096]; //We are going to read the stream in 4KB chunks
        int length = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while((length = is.read(chunk)) != -1) {
            out.write(chunk, 0, length);
        }

        byte[] content = out.toByteArray();

        out.close();

        return content;
    }

    private static final String getFileNameFromURLPath(String path) {

        String filename = path.substring(path.lastIndexOf("/") + 1, path.length());

        return filename;
    }

    private static final Map<String, Map<String, Integer>> countLibraries(List<String> javascriptLibrariesUrls) throws IOException {

        /*
         * We define a Map structure to store the libraries.
         * String key: checksum of the content.
         * Map<String, Integer> value: Map to store the name of the Javascript Library (String key)
         * and the occurrences counter (Integer value). We use this structure to prevent duplication
         */
        Map<String, Map<String, Integer>> entries = new HashMap<String, Map<String, Integer>>();

        for(String jsurl : javascriptLibrariesUrls) { //We again have to iterate all the found Javascript URLs
            URL url = new URL(jsurl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/javascript");

            //We discard the url if we can't retrieve it
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                continue;
            }

            byte[] content = readUrlContent(connection.getInputStream());

            String checksum = calculateCheckSum(content);

            if(!entries.containsKey(checksum)) { //If it's not found, we add it
                Map<String, Integer> m = new HashMap<String, Integer>();
                m.put(getFileNameFromURLPath(url.getPath()), 1); //Path of the url as key and occurrence counter as value
                entries.put(checksum, m); //We put the checksum as key with the path and occurrence counter as value
            }

            connection.disconnect();

        }

        return entries;
    }

    public static final List<Map.Entry<String, Integer>> executeSearchQuery(String queryString)
            throws IOException, NoSuchAlgorithmException {

        List<String> urls = getResultURLsFromGoogleSearchAPI(queryString);

        Map<String, Map<String, Integer>> intermediate = null;
        Map<String, Integer> value;
        Map<String, Map<String, Integer>> finalResults = new HashMap<String, Map<String, Integer>>();

        //TO - DO: For concurrency we have to execute a batch of urls into a thread
        for(String url: urls) {
            System.out.println("Processing: " + url);

            try {
                intermediate = countLibraries(getJavascriptLibrariesFromURL(url)); //We count libraries per each Google API Result URL
            }
            catch(IOException a) {
                System.out.println("\t Failed to process: " + a.getMessage());
                continue;
            }
            catch(RuntimeException b) {
                System.out.println("\t Failed to process: " + b.getMessage());
                continue;
            }

            for(String key : intermediate.keySet()) { //Then merge the intermediate results with final results

                if(!finalResults.containsKey(key)) { //If the checksum doesn't exists in finalResults
                    finalResults.put(key, intermediate.get(key)); //We add the occurences
                }
                else {
                    value = finalResults.get(key);
                    for(String key2 : value.keySet()) {
                        value.put(key2, value.get(key2) + intermediate.get(key).get(key2));
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
