package com.example.server.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.server.rag.model.RagChunkDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RagChunkDocumentMapper extends BaseMapper<RagChunkDocument> {

    @Select("""
            <script>
            SELECT c.chunk_id
            FROM rag_chunk_document c
            LEFT JOIN rag_video_version v ON c.video_id = v.video_id
            WHERE c.deleted = 0
              AND c.chunk_id IN
              <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                #{chunkId}
              </foreach>
              AND (
                c.video_id IS NULL
                OR c.version = v.current_version
              )
            </script>
            """)
    List<String> selectActiveChunkIds(@Param("chunkIds") Collection<String> chunkIds);
}
