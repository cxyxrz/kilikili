package com.kilikili.web.mapper;

import com.kilikili.web.entity.Video;
import com.kilikili.web.entity.VideoComment;
import com.kilikili.web.entity.VideoInsert;
import com.kilikili.web.entity.CreatorVideoSource;
import com.kilikili.web.entity.HistoryVideoSource;
import com.kilikili.web.entity.Danmaku;
import com.kilikili.web.entity.FavoriteVideoSource;
import com.kilikili.web.entity.RecommendSource;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface VideoMapper {
    String VIDEO_COLUMNS = "v.id, v.author_id, v.title, v.description, v.object_key, v.cover_url, v.cover_object_key, v.category_id, v.duration_seconds, v.creation_declaration, v.status, v.view_count, v.like_count, v.comment_count, v.created_at, a.nickname AS author_name, a.avatar AS author_avatar ";

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "author_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "object_key", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "duration_seconds", javaType = Integer.class),
            @Arg(column = "creation_declaration", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "view_count", javaType = Long.class),
            @Arg(column = "like_count", javaType = Long.class),
            @Arg(column = "comment_count", javaType = Long.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + VIDEO_COLUMNS + "FROM video v JOIN account a ON a.id=v.author_id WHERE v.id=#{id}")
    Video findById(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "author_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "object_key", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "duration_seconds", javaType = Integer.class),
            @Arg(column = "creation_declaration", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "view_count", javaType = Long.class),
            @Arg(column = "like_count", javaType = Long.class),
            @Arg(column = "comment_count", javaType = Long.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + VIDEO_COLUMNS + "FROM video v JOIN account a ON a.id=v.author_id WHERE v.status='PUBLISHED' ORDER BY v.created_at DESC LIMIT #{limit}")
    List<Video> listPublished(int limit);

    @Insert("INSERT INTO video(author_id,title,description,object_key,cover_object_key,category_id,duration_seconds,creation_declaration,status) VALUES(#{authorId},#{title},#{description},#{objectKey},#{coverObjectKey},#{categoryId},#{durationSeconds},#{creationDeclaration},#{status})")
    int insert(@Param("authorId") long authorId, @Param("title") String title, @Param("description") String description, @Param("objectKey") String objectKey, @Param("coverObjectKey") String coverObjectKey, @Param("categoryId") long categoryId, @Param("durationSeconds") int durationSeconds, @Param("creationDeclaration") String creationDeclaration, @Param("status") String status);

    @Insert("INSERT INTO video(author_id,title,description,object_key,cover_object_key,category_id,duration_seconds,creation_declaration,status) VALUES(#{authorId},#{title},#{description},#{objectKey},#{coverObjectKey},#{categoryId},#{durationSeconds},#{creationDeclaration},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReturningId(VideoInsert video);

    @Update("UPDATE video SET view_count=view_count+1 WHERE id=#{id}")
    int increaseViews(long id);

    @Insert("INSERT IGNORE INTO video_like(user_id,video_id) VALUES(#{userId},#{videoId})")
    int addLike(long userId, long videoId);

    @Delete("DELETE FROM video_like WHERE user_id=#{userId} AND video_id=#{videoId}")
    int deleteLike(long userId, long videoId);

    @Update("UPDATE video SET like_count=like_count+#{delta} WHERE id=#{videoId}")
    int changeLikeCount(long videoId, int delta);

    @Select("SELECT COUNT(*) FROM video_like WHERE user_id=#{userId} AND video_id=#{videoId}")
    int hasLiked(long userId, long videoId);

    @Insert("INSERT IGNORE INTO video_favorite(user_id,video_id) VALUES(#{userId},#{videoId})")
    int addFavorite(@Param("userId") long userId, @Param("videoId") long videoId);

    @Delete("DELETE FROM video_favorite WHERE user_id=#{userId} AND video_id=#{videoId}")
    int deleteFavorite(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("SELECT COUNT(*) FROM video_favorite WHERE user_id=#{userId} AND video_id=#{videoId}")
    int hasFavorited(@Param("userId") long userId, @Param("videoId") long videoId);

    @ConstructorArgs({
            @Arg(column="id", javaType=Long.class), @Arg(column="title", javaType=String.class),
            @Arg(column="author_name", javaType=String.class), @Arg(column="cover_url", javaType=String.class),
            @Arg(column="cover_object_key", javaType=String.class), @Arg(column="duration_seconds", javaType=Integer.class),
            @Arg(column="view_count", javaType=Long.class), @Arg(column="like_count", javaType=Long.class),
            @Arg(column="comment_count", javaType=Long.class), @Arg(column="favorited_at", javaType=java.time.LocalDateTime.class)
    })
    @Select("SELECT v.id,v.title,a.nickname AS author_name,v.cover_url,v.cover_object_key,v.duration_seconds,v.view_count,v.like_count,v.comment_count,f.created_at AS favorited_at FROM video_favorite f JOIN video v ON v.id=f.video_id JOIN account a ON a.id=v.author_id WHERE f.user_id=#{userId} AND v.status='PUBLISHED' ORDER BY f.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<FavoriteVideoSource> listFavorites(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_favorite f JOIN video v ON v.id=f.video_id WHERE f.user_id=#{userId} AND v.status='PUBLISHED'")
    long countFavorites(@Param("userId") long userId);

    @Insert("INSERT INTO video_comment(video_id,user_id,content) VALUES(#{videoId},#{userId},#{content})")
    int insertComment(VideoComment comment);

    @Insert("INSERT INTO video_comment(video_id,user_id,parent_id,content) VALUES(#{videoId},#{userId},#{parentId},#{content})")
    int insertReply(@Param("videoId") long videoId, @Param("userId") long userId, @Param("parentId") long parentId, @Param("content") String content);

    @Update("UPDATE video SET comment_count=comment_count+1 WHERE id=#{id}")
    int increaseComments(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "parent_id", javaType = Long.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class)
    })
    @Select("SELECT c.id,c.video_id,c.user_id,c.parent_id,c.content,c.created_at,a.nickname,a.avatar FROM video_comment c JOIN account a ON a.id=c.user_id WHERE c.video_id=#{videoId} ORDER BY c.created_at ASC")
    List<VideoComment> listComments(long videoId);

    @Select("<script>SELECT id,title,cover_object_key,status,view_count,like_count,comment_count,created_at FROM video <where>author_id=#{authorId} <if test='keyword != null and keyword != \"\"'>AND title LIKE CONCAT('%',#{keyword},'%')</if><if test='status != null and status != \"\"'>AND status=#{status}</if></where> ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="title", javaType=String.class), @Arg(column="cover_object_key", javaType=String.class), @Arg(column="status", javaType=String.class), @Arg(column="view_count", javaType=Long.class), @Arg(column="like_count", javaType=Long.class), @Arg(column="comment_count", javaType=Long.class), @Arg(column="created_at", javaType=java.time.LocalDateTime.class)})
    List<CreatorVideoSource> listMine(@Param("authorId") long authorId, @Param("keyword") String keyword, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM video <where>author_id=#{authorId} <if test='keyword != null and keyword != \"\"'>AND title LIKE CONCAT('%',#{keyword},'%')</if><if test='status != null and status != \"\"'>AND status=#{status}</if></where></script>")
    long countMine(@Param("authorId") long authorId, @Param("keyword") String keyword, @Param("status") String status);

    @Select("SELECT COUNT(*) AS videoCount, COALESCE(SUM(view_count),0) AS viewCount, COALESCE(SUM(like_count),0) AS likeCount, COALESCE(SUM(comment_count),0) AS commentCount FROM video WHERE author_id=#{authorId}")
    Map<String, Object> creatorStats(@Param("authorId") long authorId);

    @Select("SELECT COUNT(*) AS videoCount, COALESCE(SUM(like_count),0) AS likeCount FROM video WHERE author_id=#{authorId} AND status='PUBLISHED'")
    Map<String, Object> spaceStats(@Param("authorId") long authorId);

    @Select("<script>SELECT v.id,v.title,a.nickname AS author_name,v.cover_object_key,v.duration_seconds,v.view_count,v.like_count,v.comment_count,v.category_id FROM video v JOIN account a ON a.id=v.author_id WHERE v.author_id=#{authorId} AND v.status='PUBLISHED' <choose><when test='sort == \"popular\"'>ORDER BY v.view_count DESC, v.id DESC</when><otherwise>ORDER BY v.created_at DESC, v.id DESC</otherwise></choose> LIMIT #{limit} OFFSET #{offset}</script>")
    List<RecommendSource> listPublishedByAuthor(@Param("authorId") long authorId, @Param("sort") String sort,
                                                @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video WHERE author_id=#{authorId} AND status='PUBLISHED'")
    long countPublishedByAuthor(@Param("authorId") long authorId);

    @Insert("INSERT INTO video_view_history(user_id,video_id,watched_at) VALUES(#{userId},#{videoId},CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE watched_at=CURRENT_TIMESTAMP")
    int saveHistory(@Param("userId") long userId, @Param("videoId") long videoId);

    @ConstructorArgs({
            @Arg(column="id", javaType=Long.class), @Arg(column="title", javaType=String.class),
            @Arg(column="author_name", javaType=String.class), @Arg(column="cover_url", javaType=String.class),
            @Arg(column="cover_object_key", javaType=String.class), @Arg(column="duration_seconds", javaType=Integer.class),
            @Arg(column="view_count", javaType=Long.class), @Arg(column="like_count", javaType=Long.class),
            @Arg(column="comment_count", javaType=Long.class), @Arg(column="watched_at", javaType=java.time.LocalDateTime.class)
    })
    @Select("SELECT v.id,v.title,a.nickname AS author_name,v.cover_url,v.cover_object_key,v.duration_seconds,v.view_count,v.like_count,v.comment_count,h.watched_at FROM video_view_history h JOIN video v ON v.id=h.video_id JOIN account a ON a.id=v.author_id WHERE h.user_id=#{userId} AND v.status='PUBLISHED' ORDER BY h.watched_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<HistoryVideoSource> listHistory(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_view_history h JOIN video v ON v.id=h.video_id WHERE h.user_id=#{userId} AND v.status='PUBLISHED'")
    long countHistory(@Param("userId") long userId);

    @Delete("DELETE FROM video_view_history WHERE user_id=#{userId} AND video_id=#{videoId}")
    int deleteHistory(@Param("userId") long userId, @Param("videoId") long videoId);

    @Delete("DELETE FROM video_view_history WHERE user_id=#{userId}")
    int clearHistory(@Param("userId") long userId);

    @ConstructorArgs({
            @Arg(column="id", javaType=Long.class), @Arg(column="video_id", javaType=Long.class),
            @Arg(column="user_id", javaType=Long.class), @Arg(column="content", javaType=String.class),
            @Arg(column="mode", javaType=String.class), @Arg(column="color", javaType=String.class),
            @Arg(column="time_ms", javaType=Integer.class), @Arg(column="created_at", javaType=java.time.LocalDateTime.class)
    })
    @Select("SELECT id,video_id,user_id,content,mode,color,time_ms,created_at FROM video_danmaku WHERE video_id=#{videoId} ORDER BY time_ms ASC,id ASC LIMIT 1000")
    List<Danmaku> listDanmakus(@Param("videoId") long videoId);

    @Insert("INSERT INTO video_danmaku(video_id,user_id,content,mode,color,time_ms) VALUES(#{videoId},#{userId},#{content},#{mode},#{color},#{timeMs})")
    int insertDanmaku(Danmaku danmaku);

    /** 搜索联想降级方案：标题前缀匹配 */
    @Select("SELECT title FROM video WHERE status='PUBLISHED' AND title LIKE CONCAT(#{prefix},'%') ORDER BY view_count DESC, id DESC LIMIT #{limit}")
    List<String> suggestTitles(@Param("prefix") String prefix, @Param("limit") int limit);
}
