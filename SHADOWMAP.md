# How to process production tiles for Shadowmap

Start the Planetiler EC2 instance of AWS.

Run this:

```bash
cd ~/planetiler
git pull

mvn -q -DskipTests --projects planetiler-dist -am clean package

sudo mkdir /mnt
cd /mnt
sudo mkdir big
sudo mkfs.ext4 /dev/nvme1n1
sudo mount -t ext4 /dev/nvme1n1 /mnt/big
sudo chmod 777 -R /mnt/big  sudo cp -r ~/planetiler /mnt/big/planetiler

cd /mnt/big/planetiler

mkdir -p data/sources/overture

OVERTURE_VERSION=$(aws s3 ls --no-sign-request s3://overturemaps-us-west-2/release/ --region us-west-2 | awk '{print $2}' | sort | tail -n 1 | sed 's:/$::')

echo $OVERTURE_VERSION

aws configure set default.s3.max_concurrent_requests 50

sudo chmod 777 -R /mnt/big

aws s3 sync --no-sign-request s3://overturemaps-us-west-2/release/$OVERTURE_VERSION/theme=buildings/ ./data/sources/overture/theme=buildings --region us-west-2

aws s3 sync --no-sign-request s3://overturemaps-us-west-2/release/$OVERTURE_VERSION/theme=base/type=land/ ./data/sources/overture/theme=base/type=land/  --region us-west-2

sudo java -Xmx110g -XX:MaxHeapFreeRatio=40 -cp planetiler-dist/target/*-with-deps.jar com.onthegomap.planetiler.examples.ShadowmapOvertureProfile
 --area=planet --bounds=planet --nodemap-type=array --storage=ram

aws s3 cp data/overture.pmtiles s3://vector-tiles-world/overture/$OVERTURE_VERSION.pmtiles

sudo shutdown now -h
```
