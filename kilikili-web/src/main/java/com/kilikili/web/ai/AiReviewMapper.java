package com.kilikili.web.ai;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiReviewMapper {

    /**
     * 待审核且还没有 AI 初审记录的视频。结果写入 video_audit 后即不再被选中，保证每个视频只审一次。
     */
    @Select("""
            SELECT v.id,v.title,v.description,v.category_id AS categoryId,
                   v.creation_declaration AS creationDeclaration,a.nickname AS authorName
            FROM video v JOIN account a ON a.id=v.author_id
            WHERE v.status='PENDING_REVIEW'
              AND NOT EXISTS (SELECT 1 FROM video_audit au WHERE au.video_id=v.id AND au.auditor_id IS NULL)
            ORDER BY v.created_at ASC LIMIT #{limit}
            """)
    List<AiReviewSource> pendingSources(@Param("limit") int limit);

    @Insert("""
            INSERT INTO video_audit(video_id,auditor_id,result,reason,ai_score,ai_detail)
            VALUES(#{videoId},NULL,#{result},#{reason},#{score},#{detail})
            """)
    int insertAiAudit(@Param("videoId") long videoId,
                      @Param("result") String result,
                      @Param("reason") String reason,
                      @Param("score") Integer score,
                      @Param("detail") String detail);

    @Update("UPDATE video SET status=#{status} WHERE id=#{id} AND status='PENDING_REVIEW'")
    int updatePendingStatus(@Param("id") long id, @Param("status") String status);
}
