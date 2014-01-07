package me.robwilliams.watchme;

/**
 * Store the details we need for the Photo
 *
 */
public class Photo {
	private String imgurLink;
	private String deleteHash;
	public Photo(String imgurLink, String deleteHash) {
		super();
		this.imgurLink = imgurLink;
		this.deleteHash = deleteHash;
	}
	public String getImgurLink() {
		return imgurLink;
	}
	public void setImgurLink(String imgurLink) {
		this.imgurLink = imgurLink;
	}
	public String getDeleteHash() {
		return deleteHash;
	}
	public void setDeleteHash(String deleteHash) {
		this.deleteHash = deleteHash;
	}
}
