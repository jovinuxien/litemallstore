import React, { useEffect, useRef, useState } from 'react';

import './ProductGallery.scss';
interface ProductGalleryProps {
  gallery: string[];
  name: string;
  htmlContent: string;
}
const extactImagesUrls = (htmlContent): string[] => {
  const parser = new DOMParser();
  const doc = parser.parseFromString(htmlContent, 'text/html');
  const imgElements = doc.getElementsByTagName('img');
  return Array.from(imgElements).map(img => img.getAttribute('src'));
};

const ProductGallery: React.FC<ProductGalleryProps> = ({ gallery, name, htmlContent }) => {
  const imageUrls = extactImagesUrls(htmlContent);
  const [mainImage, setMainImage] = React.useState('');
  const [zoomPosition, setZoomPosition] = useState({ x: 0, y: 0 });
  const [isZoomed, setIsZoomed] = useState(false);
  const mainImageRef = useRef<HTMLImageElement>(null);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (mainImageRef.current) {
      const { left, top, width, height } = mainImageRef.current.getBoundingClientRect();
      const x = ((e.clientX - left) / width) * 100;
      const y = ((e.clientY - top) / height) * 100;
      setZoomPosition({ x, y });
    }
  };
  const allImages = [...new Set([...gallery, ...imageUrls.slice(0, 15)])];

  useEffect(() => {
    if (gallery && gallery.length > 0) {
      setMainImage(gallery[0]);
    }
  }, [gallery]);

  return (
    <>
      <div className='product-gallery'>
        <div className='gallery-container'>
          <div className='thumbnail-container'>
            {allImages.slice(0, 5).map((image, index) => (
              <img
                key={index}
                src={image}
                className={`thumbnail ${image === mainImage ? 'active' : ''}`}
                alt={`${name} - view ${index + 1}`}
                onClick={() => setMainImage(image)}
              />
            ))}
          </div>
          <div className='main-image-container' onMouseEnter={() => setIsZoomed(true)} onMouseLeave={() => setIsZoomed(false)} onMouseMove={handleMouseMove}>
            <img ref={mainImageRef} src={mainImage} className='main-image' alt={name} />
            {isZoomed && (
              <div
                className='zoom-overlay'
                style={{
                  backgroundImage: `url(${mainImage})`,
                  backgroundPosition: `${zoomPosition.x}% ${zoomPosition.y}%`,
                }}
              />
            )}
          </div>
        </div>
        <div className='product-image-gallery'>
          {allImages.slice(5).map((url, index) => (
            <div key={index} className='gallery-item'>
              <img src={url} alt={`Product image ${index + 6}`} onClick={() => setMainImage(url)} />
            </div>
          ))}
        </div>
      </div>
    </>
  );
};

export default ProductGallery;
