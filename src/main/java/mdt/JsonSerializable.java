package mdt;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JsonSerializable {
	public String toJsonString();
	public void loadFromJsonString(String jsonStr);
}
