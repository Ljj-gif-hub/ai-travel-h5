package org.example.traveljava.dto;

import java.util.List;

/**
 * 城市DTO — 统一返回结构
 */
public class CityDTO {

    /** 省份/分组下的城市列表 */
    public static class ProvinceGroup {
        private String key;           // municipalities / hk_macau_tw / 省份名
        private String label;         // 直辖市 / 港澳台 / 广东
        private String type;          // "group" | "province"
        private List<CityItem> items; // 分组下的子项

        public ProvinceGroup() {}
        public ProvinceGroup(String key, String label, String type, List<CityItem> items) {
            this.key = key; this.label = label; this.type = type; this.items = items;
        }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<CityItem> getItems() { return items; }
        public void setItems(List<CityItem> items) { this.items = items; }
    }

    /** 城市/地区项 */
    public static class CityItem {
        private String name;
        private List<CityCard> cities;

        public CityItem() {}
        public CityItem(String name, List<CityCard> cities) {
            this.name = name; this.cities = cities;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<CityCard> getCities() { return cities; }
        public void setCities(List<CityCard> cities) { this.cities = cities; }
    }

    /** 城市卡片 */
    public static class CityCard {
        private String name;
        private String image;  // Unsplash 图片 URL

        public CityCard() {}
        public CityCard(String name) {
            this.name = name;
            this.image = "https://source.unsplash.com/160x100/?" + name + ",travel,city";
        }
        public CityCard(String name, String image) {
            this.name = name; this.image = image;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
    }

    /** 境外大洲 */
    public static class ContinentGroup {
        private String name;
        private List<CountryGroup> countries;

        public ContinentGroup() {}
        public ContinentGroup(String name, List<CountryGroup> countries) {
            this.name = name; this.countries = countries;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<CountryGroup> getCountries() { return countries; }
        public void setCountries(List<CountryGroup> countries) { this.countries = countries; }
    }

    /** 国家分组 */
    public static class CountryGroup {
        private String name;
        private List<CityCard> cities;

        public CountryGroup() {}
        public CountryGroup(String name, List<CityCard> cities) {
            this.name = name; this.cities = cities;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<CityCard> getCities() { return cities; }
        public void setCities(List<CityCard> cities) { this.cities = cities; }
    }

    /** 境内响应 */
    public static class DomesticResponse {
        private List<String> hotCities;
        private List<ProvinceGroup> groups;
        private List<ProvinceGroup> provinces;

        public DomesticResponse() {}
        public DomesticResponse(List<String> hotCities, List<ProvinceGroup> groups, List<ProvinceGroup> provinces) {
            this.hotCities = hotCities; this.groups = groups; this.provinces = provinces;
        }
        public List<String> getHotCities() { return hotCities; }
        public void setHotCities(List<String> hotCities) { this.hotCities = hotCities; }
        public List<ProvinceGroup> getGroups() { return groups; }
        public void setGroups(List<ProvinceGroup> groups) { this.groups = groups; }
        public List<ProvinceGroup> getProvinces() { return provinces; }
        public void setProvinces(List<ProvinceGroup> provinces) { this.provinces = provinces; }
    }

    /** 境外响应 */
    public static class OverseasResponse {
        private List<String> hotCities;
        private List<ContinentGroup> continents;

        public OverseasResponse() {}
        public OverseasResponse(List<String> hotCities, List<ContinentGroup> continents) {
            this.hotCities = hotCities; this.continents = continents;
        }
        public List<String> getHotCities() { return hotCities; }
        public void setHotCities(List<String> hotCities) { this.hotCities = hotCities; }
        public List<ContinentGroup> getContinents() { return continents; }
        public void setContinents(List<ContinentGroup> continents) { this.continents = continents; }
    }

    /** 搜索结果 */
    public static class SearchResult {
        private String type; // "domestic" | "overseas"
        private String name;
        private String parent; // 所属省份/国家
        private String image;

        public SearchResult() {}
        public SearchResult(String type, String name, String parent) {
            this.type = type; this.name = name; this.parent = parent;
            this.image = "https://source.unsplash.com/160x100/?" + name + ",travel,city";
        }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParent() { return parent; }
        public void setParent(String parent) { this.parent = parent; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
    }

    /** 定位响应 */
    public static class LocationResult {
        private String city;
        private String province;
        private String district;
        private String address;   // 完整可读地址：广东省深圳市南山区
        private String street;    // 街道信息
        private double lat;
        private double lng;

        public LocationResult() {}
        public LocationResult(String city, String province, double lat, double lng) {
            this.city = city; this.province = province; this.lat = lat; this.lng = lng;
        }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }
    }
}
