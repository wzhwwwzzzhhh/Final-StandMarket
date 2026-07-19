FROM elasticsearch:8.17.0

# 安装 IK 分词插件
RUN elasticsearch-plugin install --batch \
    https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.17.0/elasticsearch-analysis-ik-8.17.0.zip

# 安装拼音分词插件
RUN elasticsearch-plugin install --batch \
    https://github.com/medcl/elasticsearch-analysis-pinyin/releases/download/v8.17.0/elasticsearch-analysis-pinyin-8.17.0.zip
