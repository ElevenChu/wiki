package com.elevenchu.wiki.service;

import com.elevenchu.wiki.mapper.EbookMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class EbookService {
    @Resource
    EbookMapper ebookMapper;
}
