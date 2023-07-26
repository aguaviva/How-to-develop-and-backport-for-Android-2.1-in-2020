class cvMat
{
    uint8_t *pData;
public:
    cvMat(uint32_t width, uint32_t height, uint32_t channels)
    {
        pData = (uint8_t *)malloc(width*height*channels);
    }
    ~cvMat()
    {
        free(pData);
    }
};