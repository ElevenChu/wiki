package com.elevenchu.wiki.service;

import com.elevenchu.wiki.domain.Content;
import com.elevenchu.wiki.domain.Doc;
import com.elevenchu.wiki.domain.DocExample;
import com.elevenchu.wiki.exception.BusinessException;
import com.elevenchu.wiki.exception.BusinessExceptionCode;
import com.elevenchu.wiki.mapper.ContentMapper;
import com.elevenchu.wiki.mapper.DocMapper;
import com.elevenchu.wiki.mapper.DocMapperCust;
import com.elevenchu.wiki.req.DocQueryReq;
import com.elevenchu.wiki.req.DocSaveReq;
import com.elevenchu.wiki.resp.DocQueryResp;
import com.elevenchu.wiki.resp.PageResp;
import com.elevenchu.wiki.util.CopyUtil;
import com.elevenchu.wiki.util.RedisUtil;
import com.elevenchu.wiki.util.RequestContext;
import com.elevenchu.wiki.websocket.WebSocketServer;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
//import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import com.elevenchu.wiki.util.SnowFlake;
import org.springframework.web.context.annotation.RequestScope;

@Service
public class DocService {
    private static final Logger LOG = LoggerFactory.getLogger(DocService.class);
    @Resource
    DocMapper docMapper;
    @Resource
    ContentMapper contentMapper;
    /**
     * 阅读，文章阅读数+1接口
     */
    @Resource
    private DocMapperCust docMapperCust;

    @Resource
    private SnowFlake snowFlake;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private WebSocketServer webSocketServer;
    @Resource
    private WsService wsService;
//     @Resource
//     private RocketMQTemplate rocketMQTemplate;

    public List<DocQueryResp> all(Long ebookId) {
        DocExample docExample = new DocExample();
        docExample.createCriteria().andEbookIdEqualTo(ebookId);
        docExample.setOrderByClause("sort asc");
        List<Doc> docList = docMapper.selectByExample(docExample);

        // 列表复制
        List<DocQueryResp> list = CopyUtil.copyList(docList, DocQueryResp.class);

        return list;
    }

    public PageResp<DocQueryResp> list(DocQueryReq req) {
        DocExample docExample = new DocExample();
        docExample.setOrderByClause("sort asc");
        DocExample.Criteria criteria = docExample.createCriteria();
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Doc> docList = docMapper.selectByExample(docExample);

        PageInfo<Doc> pageInfo = new PageInfo<>(docList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        // List<DocResp> respList = new ArrayList<>();
        // for (Doc doc : docList) {
        //     // DocResp docResp = new DocResp();
        //     // BeanUtils.copyProperties(doc, docResp);
        //     // 对象复制
        //     DocResp docResp = CopyUtil.copy(doc, DocResp.class);
        //
        //     respList.add(docResp);
        // }

        // 列表复制
        List<DocQueryResp> list = CopyUtil.copyList(docList, DocQueryResp.class);

        PageResp<DocQueryResp> pageResp = new PageResp();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);

        return pageResp;
    }

    /**
     * 保存
     */
    @Transactional
    public void save(DocSaveReq req) {
        Doc doc = CopyUtil.copy(req, Doc.class);
        Content content = CopyUtil.copy(req, Content.class);
        if (ObjectUtils.isEmpty(req.getId())) {
            // 新增
            doc.setId(snowFlake.nextId());
            doc.setViewCount(0);
            doc.setVoteCount(0);
            docMapper.insert(doc);

            content.setId(doc.getId());
            contentMapper.insert(content);
        } else {
            // 更新
            docMapper.updateByPrimaryKey(doc);
            int count = contentMapper.updateByPrimaryKeyWithBLOBs(content);
            if (count == 0) {
                contentMapper.insert(content);
            }
        }
    }

    public void delete(Long id) {
        docMapper.deleteByPrimaryKey(id);
    }

    public void delete(List<String> ids) {
        DocExample docExample = new DocExample();
        DocExample.Criteria criteria = docExample.createCriteria();
        List<Long> list=new ArrayList<>();
        for (String id:ids
             ) {
            list.add(Long.parseLong(id));

        }
        criteria.andIdIn(list);
        docMapper.deleteByExample(docExample);
    }

    public String findContent(Long id) {
        Content content = contentMapper.selectByPrimaryKey(id);
        // 文档阅读数+1
        docMapperCust.viewCountIncrease(id);
        if (ObjectUtils.isEmpty(content)) {
            return "";
        } else {
            return content.getContent();
        }
    }
    /**
     * 点赞
     */
    public void vote(Long id) {
        // docMapperCust.increaseVoteCount(id);
        // 远程IP+doc.id作为key，24小时内不能重复
        String ip = RequestContext.getRemoteAddr();
        if (redisUtil.validateRepeat("DOC_VOTE_" + id + "_" + ip, 5000)) {

        } else {
            throw new BusinessException(BusinessExceptionCode.VOTE_REPEAT);
        }

        // 推送消息
        Doc docDb = docMapper.selectByPrimaryKey(id);
        String logId = MDC.get("LOG_ID");
        wsService.sendInfo("【" + docDb.getName() + "】被点赞！", logId);
        // rocketMQTemplate.convertAndSend("VOTE_TOPIC", "【" + docDb.getName() + "】被点赞！");
    }

    /**
     * 更新电子书信息
     * 将文档信息存入电子书
     */
    public void updateEbookInfo() {
        docMapperCust.updateEbookInfo();
    }
}
