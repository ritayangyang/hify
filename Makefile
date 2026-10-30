# ============================================================
# Hify 一键本地部署打包
#   make package   构建后端 jar + 前端 dist，产出 target/hify-<version>.tar.gz
# 目标机器只需 JDK 21+ 与外部 MySQL（Redis/pgvector 本期可选）
# ============================================================
SHELL := /bin/bash

VERSION  := $(shell sed -n '/<\/parent>/,$$p' pom.xml | grep -m1 -o '<version>[^<]*' | sed 's/<version>//')
PKG_NAME := hify
PKG_DIR  := target/$(PKG_NAME)
PKG_TGZ  := target/hify-$(VERSION).tar.gz

BACKEND_JAR := hify-bootstrap/target/hify-bootstrap-$(VERSION).jar

.PHONY: package backend frontend assemble clean

package: backend frontend assemble

# 后端：全模块构建可执行 fat jar（hify-bootstrap repackage）
backend:
	mvn -q -pl hify-bootstrap -am clean package -DskipTests

# 前端：已存在 node_modules 时跳过安装；产物 hify-web/dist
frontend:
	@if [ ! -d hify-web/node_modules ]; then cd hify-web && npm ci; fi
	cd hify-web && npm run build

# 组装部署目录并打 tar 包
assemble:
	@test -f $(BACKEND_JAR) || { echo "后端 jar 不存在，请先 make backend"; exit 1; }
	@test -f hify-web/dist/index.html || { echo "前端 dist 不存在，请先 make frontend"; exit 1; }
	rm -rf $(PKG_DIR) $(PKG_TGZ)
	mkdir -p $(PKG_DIR)/bin $(PKG_DIR)/conf $(PKG_DIR)/app $(PKG_DIR)/logs $(PKG_DIR)/run
	cp $(BACKEND_JAR) $(PKG_DIR)/app/hify-backend.jar
	cp -R hify-web/dist $(PKG_DIR)/dist
	cp deploy/local/bin/start.sh deploy/local/bin/stop.sh $(PKG_DIR)/bin/
	cp deploy/local/conf/application.yml $(PKG_DIR)/conf/
	cp deploy/local/conf/hify.env.example $(PKG_DIR)/conf/
	cp deploy/local/README-DEPLOY.md $(PKG_DIR)/README-DEPLOY.md
	chmod +x $(PKG_DIR)/bin/start.sh $(PKG_DIR)/bin/stop.sh
	tar -czf $(PKG_TGZ) -C target $(PKG_NAME)
	@echo ""
	@echo "打包完成: $(PKG_TGZ)"
	@echo "部署: scp 到目标机后 tar xzf 解包，cp conf/hify.env.example conf/hify.env 填连接信息，bin/start.sh"

clean:
	rm -rf $(PKG_DIR) $(PKG_TGZ)
