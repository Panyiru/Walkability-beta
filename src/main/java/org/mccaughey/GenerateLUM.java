package org.mccaughey;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MessageListener;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.mccaughey.ActiveMQ.Sender;
import org.mccaughey.connectivity.ConnectivityIndex;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.density.DwellingDensity;
import org.mccaughey.landuse.LandUseMix;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GenerateLUM implements MessageListener,Runnable {
    // URL of the JMS server
    private static String url = ActiveMQConnection.DEFAULT_BROKER_URL;
    // default broker URL is : tcp://localhost:61616"

    // Name of the queue
    private static String subject_Receive = "PolygonLUM";
    private Connection connection_Receive;
    private static Sender sender;

    public static void main(String[] argv){

        GenerateLUM l = new GenerateLUM();
        Thread receiver = new Thread(l);
        receiver.start();

        sender = new Sender("LUMZscore");
        Thread senderThread = new Thread(sender);
        senderThread.start();


    }

    public void run(){
        try{
            // Getting JMS connection from the server
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
            connection_Receive = connectionFactory.createConnection();
            connection_Receive.start();

            // Creating session for receiving messages
            Session session = connection_Receive.createSession(false,
                    Session.DUPS_OK_ACKNOWLEDGE);

            // Getting the queue
            Destination destination = session.createQueue(subject_Receive + "?consumer.prefetchSize=1");

            // MessageConsumer is used for receiving (consuming) messages
            MessageConsumer consumer = session.createConsumer(destination);

            consumer.setMessageListener(this);
            System.out.println("Waiting for messages...");
        }
        catch(JMSException e){
            e.printStackTrace(System.out);
        }


    }

    @Override
    public void onMessage(Message message){
        // We will be using TestMessage in our example. MessageProducer sent us a TextMessage
        // so we must cast to it to get access to its .getText() method.
        try{
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                System.out.println("Received message '" + textMessage.getText() + "'");

                String string = textMessage.getText();
                InputStream inputStream = new ByteArrayInputStream(string.getBytes(Charset.forName("UTF-8")));

                FeatureJSON fjson = new FeatureJSON();
                SimpleFeature region = fjson.readFeature(inputStream);

                URL landUseURL = new File("./src/main/java/org/mccaughey/MB_WA_2006_census_projected.shp").toURI().toURL();
                File landUseShapeFile = new File(landUseURL.toURI());
                FileDataStore landUseDataStore = FileDataStoreFinder.getDataStore(landUseShapeFile);

                List<String> classifications = new ArrayList<String>();
                classifications.add("Parkland");
                classifications.add("Residential");
                classifications.add("Education");
                classifications.add("Commercial");
                classifications.add("Industrial");
                classifications.add("Hospital/Medical");

                SimpleFeature landUseRegionFeature = LandUseMix.summarise(landUseDataStore.getFeatureSource(), region,
                        classifications, "CATEGORY");

                Double lum = (Double) landUseRegionFeature.getAttribute("LandUseMixMeasure");

                sender.sendMessage("lum: "+lum.toString());

                landUseDataStore.dispose();

                //Write the feature into the file
                File file_lum = new File("./src/main/java/org/mccaughey/output/lumOMS.geojson");
                FileOutputStream fout_3 = new FileOutputStream(file_lum, true);
                fout_3.write((fjson.toString(landUseRegionFeature)+",").getBytes());
                fout_3.flush();
                fout_3.close();

            }
            else{
                connection_Receive.close();
            }
        }
        catch(JMSException e){
            e.printStackTrace(System.out);
        }
        catch(IOException e){
            e.printStackTrace(System.out);
        }
        catch(URISyntaxException e1){
            System.out.println(e1.getMessage());
        }

    }

    public void close(){
        try {
            connection_Receive.close();
            sender.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

}


