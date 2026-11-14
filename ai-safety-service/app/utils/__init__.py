"""
工具子包：帧缓冲、截图、防抖、ROI 过滤等通用组件。

导出说明：
    frame_buffer.FrameBuffer  线程安全的帧环形缓冲
    debounce.Debouncer        违规防抖器（类型+位置粗粒度）
    roi_filter.ROIFilter      ROI 多边形区域过滤
    screenshot                截图保存工具（全图 + bbox 裁剪）
"""
