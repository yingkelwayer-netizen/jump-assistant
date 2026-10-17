package local.jump.offline;
final class LicenseTerms {
    static final long WEEK=604800000L;
    static boolean valid(int version,String source,long activated,long expires,long checked){
        if(activated<=0||expires<=activated||checked<activated||checked>=expires)return false;
        long duration=expires-activated;
        return version==1?duration==WEEK:version==2&&"account".equals(source);
    }
    private LicenseTerms(){}
}
