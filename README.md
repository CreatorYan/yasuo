# yasuo
安卓里的图片压缩算法
适用于朋友圈这种，需要上传图片到自己服务器上的时候，上传一个缩略图和一个所谓的原图。
所用的算法是建立在Luban算法之上的，经过改良后现在有四种压缩级别，如下：
 * 一：压缩结果比较稳定，返回的图片文件大致在200K~600K之间
 * 二：压缩结果比较稳定，返回的图片文件大致在60K~120K之间
 * 三：压缩结果不稳定，返回的图片文件大致在50K~300K之间
 * 四：压缩结果比较稳定，返回的图片文件大小大致在50~110K之间
 
 案例中选择的是第一种作为原图，第四种作为缩略图。一般情况下也就是使用这两种压缩级别。
