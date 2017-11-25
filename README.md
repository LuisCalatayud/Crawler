# Web Crawler - Scalable Capital Assignment

Explanation of steps took into account to resolve the assignment:

## First Step: Get the results from Google

Naturally I instantly though about the usage of the Google Search API’s to get the results for a certain string query.  In order to do this I had to set a Google API Key from their website and later on, I had to create a Custom Search Engine to search all the web. The Google API was straightforward to use as it provides a simple URL interface to get the search results in JSON format. This is the URL used:
https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&start=%s&q=%s
Meaning of parameters:
•	Key: Google API key set to use the API.
•	Cx: Custom Search Engine to search all the web.
•	Start: Results start page (by default it’s 1)
•	Q: Search term.
A HttpURLConnection was made to fetch the results given by the API. These results where formatted in JSON and had to be “parsed” to be easily manipulated by the program.

## Second Step: Read the JSON

Java provides an API to process JSON via the package javax.json which contains all the interfaces used for JSON manipulation. As this package contains only intefaces (i.e. functions skeletons with no actual working functionality)  I had to download an implementation of the packages. The org.glassfish javax.json implementation was chosen because it is proved to be reliable.
The function getResultURLsFromGoogleSearchAPI(String queryString) is the one that fetchs the results from the Google API and returns a List of the webpages URLS. The resulting JSON Object has the following needed attributes:
•	items: a collection of search results.
•	link: a string containing the URL for each search result.
I stored all the links into a List.
 
## Third Step: Fetching contents of search results webpages

After I got all the resulting links, fetching the HTML contents was the next step to take. What I did was to open an HttpURLConnection and get the HTML line by line using a Buffered Reader. The function delegated to this task is getJavascriptLibrariesFromURL(String url) 

## Fourth Step: Extract Javascript Libraries

As I was getting the HTML line by line a simple idea to filter innecessary elements was to check if the line contained the following tag “<script”. Even though this needs to be improved because it may happen that there are multiple script tags in one line. After filtering I need to extract the src attribute of the found script tags, the method used was to match a regular expression and later on extract the src links.
To prevent duplication of a script inside a page what I did was to first check the src links obtained using a LookUp table (using a HashTable in Java), first searching them in the LookUp table, if it is found inside the table then I discard it, if not I add it. This is a naive first filter but also was needed to check the contents for each Javascript file and making a checksum of it, then I store the checksum inside another lookup table along with the filename and occurrence counter. The checksum assures contents are the same if the file hasn’t been modified. This is useful to check libraries which have the same content but different names. countLibraries(List<String> javascriptLibrariesUrls) is in charge of these functionalities.
The structure used is described:
Map<String, Map<String, Integer>> structure
Datatypes meaning:
Map<Checksum, Map<Javascritp Library URL, Ocurrence Counter>>

## Fifth Step: Merge results

For each of the URLS obtained in step number two, we obtain the occurrence of javascript libraries. Now we have to merge the results in a final structure in which we sum the occurrences of each library in the urls extracted. 
