package me.robwilliams.watchme;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

@SuppressWarnings("deprecation")
public class WatchMe {
	private static final int TARGET_WIDTH = 300;
	private static String IMGUR_CLIENT_ID = "";
	private static String CLIENT_SECRET = "";
	static {
		Properties prop = new Properties();
		try {
			InputStream configFile = WatchMe.class.getResourceAsStream("/config.properties");
			prop.load(configFile);
		} catch (Exception ex) {
			System.err.println("Problem loading config file");
			System.exit(-1);
		}
		IMGUR_CLIENT_ID = prop.getProperty("IMGUR_CLIENT_ID");
		CLIENT_SECRET = prop.getProperty("CLIENT_SECRET");
		//System.out.println("Imgur client id = " + IMGUR_CLIENT_ID);
		//System.out.println("Client secret = " + CLIENT_SECRET);
	}
	
	/**
	 * Take screenshot of current desktop.
	 * @return screenshot image
	 * @throws Exception
	 */
	private static BufferedImage takeScreenshot() throws Exception {
		return new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
	}
	
	/**
	 * Resize the given screenshot to the TARGET_WIDTH, constraining proportions.
	 * @param screenshot
	 * @return
	 */
	private static BufferedImage resizeScreenshot(BufferedImage screenshot) {
		// Credit: http://stackoverflow.com/a/4216315
		float scaleFactor = (float)(TARGET_WIDTH) / screenshot.getWidth();
		int targetHeight = (int) (screenshot.getHeight() * scaleFactor);
		BufferedImage resized = new BufferedImage(TARGET_WIDTH, targetHeight, screenshot.getType());
	    Graphics2D g = resized.createGraphics();
	    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	    g.drawImage(screenshot, 0, 0, TARGET_WIDTH, targetHeight, 0, 0, screenshot.getWidth(), screenshot.getHeight(), null);
	    g.dispose();
	    return resized;
	}	
	
	/**
	 * Convert image to Base64 so that it can be sent via HTTP POST parameters to Imgur.
	 * @param image
	 * @return
	 * @throws Exception
	 */
	private static String imageToBase64(BufferedImage image) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStream b64 = new Base64OutputStream(os);
		ImageIO.write(image, "jpg", b64);
		return os.toString("UTF-8");
	}
	
	/**
	 * Upload a screenshot image to imgur, via anonymous API.
	 * @param client
	 * @param screenshot
	 * @return
	 * @throws Exception
	 */
	private static Photo uploadToImgur(HttpClient client, BufferedImage screenshot) throws Exception {
		String url = "https://api.imgur.com/3/upload.json";
		HttpPost post = new HttpPost(url);
	 
		// add auth header for IMGUR api
		post.setHeader("Authorization", "Client-ID " + IMGUR_CLIENT_ID);
	 
		List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("image", imageToBase64(screenshot)));
		post.setEntity(new UrlEncodedFormEntity(urlParameters));
	 
		HttpResponse response = client.execute(post);
		// For debugging, print response headers
		Header[] headers = response.getAllHeaders();
		for (Header header : headers) {
			System.out.println(header);
		}
		if (response.getStatusLine().getStatusCode() != 200) {
			// TODO: some better error handling
			EntityUtils.consumeQuietly(response.getEntity());
			throw new Exception("Failed to upload to imgur: " + response.getStatusLine());
		} else {
			// Read response JSON stream
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			StringBuffer responseString = new StringBuffer();
			String line = "";
			while ((line = rd.readLine()) != null) {
				responseString.append(line);
			}
			// Parse response JSON
			
			JSONObject responseObj = (JSONObject)(JSONValue.parse(responseString.toString()));
			JSONObject imgurDataObj = (JSONObject)(responseObj.get("data"));
			return new Photo((String)(imgurDataObj.get("link")), (String)(imgurDataObj.get("deletehash")));
		}
	}
	
	/**
	 * Upload photo metadata to w4tchme server, so it can display and delete it at will.
	 * @param client
	 * @param photo
	 * @throws Exception
	 */
	private static void uploadToWatchMeServer(HttpClient client, Photo photo) throws Exception {
		String url = "https://w4tchme.herokuapp.com/photos";
		HttpPost post = new HttpPost(url);
	 
		// add auth header for WatchMe
		post.setHeader("Authorization", CLIENT_SECRET);
	 
		List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("imgurLink", photo.getImgurLink()));
		urlParameters.add(new BasicNameValuePair("deleteHash", photo.getDeleteHash()));
		post.setEntity(new UrlEncodedFormEntity(urlParameters));
	 
		HttpResponse response = client.execute(post);
		EntityUtils.consumeQuietly(response.getEntity());
		if (response.getStatusLine().getStatusCode() != 200) {
			// TODO: some better error handling
			throw new Exception("Failed to send to w4tchme server: " + response.getStatusLine());
		} else {
			System.out.println("Successfully uploaded photo at " + new Date());
		}
	}
	
	public static void main(String[] args) throws Exception {
		// create client to use for entire app life
		HttpClient client = new DefaultHttpClient();
		
		// Main "event" loop
		// Take screenshot once every two minutes, resize it, 
		// and then upload to imgur & watchme server.
		while(true) {
			try {
				BufferedImage screenshot = takeScreenshot();
				BufferedImage resizedScreenshot = resizeScreenshot(screenshot);
				Photo photo = uploadToImgur(client, resizedScreenshot);
				uploadToWatchMeServer(client, photo);
				Thread.sleep(2 * 60 * 1000); // sleep two minutes
			} catch (Exception ex) {
				System.err.println("Encountered exception at " + new Date());
				ex.printStackTrace();
			}
		}
	}
	
}