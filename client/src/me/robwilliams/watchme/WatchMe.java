package me.robwilliams.watchme;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class WatchMe {
	private static final int TARGET_WIDTH = 300;
	private static String IMGUR_CLIENT_ID = "";
	static {
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("config.properties"));
		} catch (Exception ex) {
			System.exit(-1);
		}
		IMGUR_CLIENT_ID = prop.getProperty("IMGUR_CLIENT_ID");
		System.out.println("Imgur client id = " + IMGUR_CLIENT_ID);
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
	
	private static String imageToBase64(BufferedImage image) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStream b64 = new Base64OutputStream(os);
		ImageIO.write(image, "jpg", b64);
		return os.toString("UTF-8");
	}
	
	private static void uploadToImgur(HttpClient client, BufferedImage screenshot) throws Exception {
		String url = "https://api.imgur.com/3/upload.json";
		HttpPost post = new HttpPost(url);
	 
		// add auth header for IMGUR api
		post.setHeader("Authorization", "Client-ID " + IMGUR_CLIENT_ID);
	 
		List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("image", imageToBase64(screenshot)));
		post.setEntity(new UrlEncodedFormEntity(urlParameters));
	 
		HttpResponse response = client.execute(post);
		if (response.getStatusLine().getStatusCode() != 200) {
			// TODO: some better error handling
			throw new Exception();
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
			System.out.println("Uploaded to: " + imgurDataObj.get("link"));
			System.out.println("Delete hash: " + imgurDataObj.get("deletehash"));
		}
	}
	
	public static void main(String[] args) throws Exception {
		// create client to use for entire app life
		HttpClient client = new DefaultHttpClient();
		
		// TODO: wrap this whole thing in a loop that executes once every 1 minute
		// TODO: error handling in main loop, for now we can just show message box with error
		BufferedImage screenshot = takeScreenshot();
		BufferedImage resizedScreenshot = resizeScreenshot(screenshot);
		uploadToImgur(client, resizedScreenshot);
		
	}

	
}