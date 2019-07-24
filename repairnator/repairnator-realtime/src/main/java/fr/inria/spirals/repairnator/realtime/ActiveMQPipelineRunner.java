package fr.inria.spirals.repairnator.realtime;

import fr.inria.jtravis.entities.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * This class will take the builds qualified by the inspectBuild
 * and submit to an ActiveMQ queue for the repairnator-worker
 * to run on Kubernetes.
 */
public class ActiveMQPipelineRunner implements PipelineRunner<Boolean,Build> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQPipelineRunner.class);
    private static final int DELAY_BETWEEN_DOCKER_IMAGE_REFRESH = 60; // in minutes
    private static String url = "tcp://localhost:61616"; //Default address for Activemq server
    private static String queueName = "pipeline";  //Default pipeline queue name to push ids to

    public ActiveMQPipelineRunner(){}

    public void setUrlAndQueue(String url, String queueName) {
        this.url = url;
        this.queueName = queueName;
    }

    public Boolean testConnection() {
        try {
            /*
             * Getting JMS connection from the JMS server and starting it
             */
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(this.url);
            Connection connection = connectionFactory.createConnection();
            connection.start();

            /*
             * Creating a non transactional session to send/receive JMS message.
             */
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            /*
             * The queue will be created automatically on the server.
             */
            Destination destination = session.createQueue("Con_Test");

            /*
             * Destination represents here our queue 'MESSAGE_QUEUE' on the JMS server.
             * 
             * MessageProducer is used for sending messages to the queue.
             */
            MessageProducer producer = session.createProducer(destination);
            TextMessage message = session.createTextMessage("Testing");

            producer.send(message);
            connection.close();
            LOGGER.warn("Connection to activemq Succeeded"); 
            LOGGER.warn("Connected to url: " + this.url + " and queueName: " + this.queueName);

            return true;
        }catch(Exception e){
            LOGGER.warn("Tried to connect to url " + this.url);
            LOGGER.warn("Connection to activemq failed, please double check the ActiveMQ server"); 
            return false;   
        }
    }

    public Boolean submitBuild(Build build) {
        try {
            /*
             * Getting JMS connection from the JMS server and starting it
             */
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(this.url);
            Connection connection = connectionFactory.createConnection();
            connection.start();

            /*
             * Creating a non transactional session to send/receive JMS message.
             */
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            /*
             * The queue will be created automatically on the server.
             */
            Destination destination = session.createQueue(this.queueName);

            /*
             * Destination represents here our queue 'MESSAGE_QUEUE' on the JMS server.
             * 
             * MessageProducer is used for sending messages to the queue.
             */
            MessageProducer producer = session.createProducer(destination);
            TextMessage message = session.createTextMessage(Long.toString(build.getId()));

            producer.send(message);

            LOGGER.info("Build id '" + message.getText() + ", Sent Successfully to the Queue");
            connection.close();
            return true;
        } catch(JMSException jsme) {
            LOGGER.info("Failed to submit build, please double check ActiveMQ server");
            return false;
        }
    }

}
