## install docker in aws Linux ##
# Cập nhật hệ thống
sudo dnf update -y
# Cài Docker từ repo Amazon Linux Extras
sudo dnf install -y docker
# Khởi động Docker và bật chạy cùng hệ thống
sudo systemctl start docker
sudo systemctl enable docker
# Kiểm tra Docker đã cài chưa
docker --version


### build docker ##
docker build -t kira-service .
docker tag kira-service kira2308/kira-service
docker login
docker push kira2308/kira-service


### deploy aws ###
sudo docker pull kira2308/kira-service
sudo docker run -d -p 2308:2308 -e DB_HOST=... -e DB_PORT=12125 -e DB_USERNAME=... -e DB_PASSWORD=... --name kira-be-aws --hostname kira-be-aws kira2308/kira-service
