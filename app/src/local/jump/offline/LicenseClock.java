package local.jump.offline;
final class LicenseClock {
 static long now(long server,long anchorWall,long anchorElapsed,int anchorBoot,long wall,long elapsed,int boot,long maxWall){
  if(server<=0||anchorWall<=0||anchorElapsed<0)return Long.MAX_VALUE;
  if(anchorBoot==boot&&elapsed>=anchorElapsed)return server+elapsed-anchorElapsed;
  if(wall<maxWall-300000L||wall<anchorWall-300000L)return Long.MAX_VALUE;
  return server+Math.max(0,wall-anchorWall);
 }
 private LicenseClock(){}
}
