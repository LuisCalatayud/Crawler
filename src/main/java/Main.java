import javafx.util.Pair;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Created by LuisAlejandro on 21/11/2017.
 */
public class Main {

    public static void main(String args[]) {

        String query;

        try {
            query = args[0];
        }
        catch(ArrayIndexOutOfBoundsException e) {
            System.out.println("Query string wasn't provided");
            return ;
        }

        Crawler crawler = new Crawler();


        try {
            List<Map.Entry<String, Integer>> results = crawler.executeSearchQuery(query);

            for(int i = 0; i < 5; i++) { //We print the 5 most used Javascript libraries
                System.out.println(results.get(i).getKey() + ": " + results.get(i).getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
