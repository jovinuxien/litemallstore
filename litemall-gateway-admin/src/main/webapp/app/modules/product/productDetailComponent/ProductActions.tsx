import React from 'react';

interface ProductActionsProps {
  quantity: number;
  setQuantity: (quantity: number) => void;
  handleAddToCart: () => void;
  handleBuyNow: () => void;
}

const ProductActions: React.FC<ProductActionsProps> = ({ quantity, setQuantity, handleAddToCart, handleBuyNow }) => (
  <div className='mb-3'>{/* Quantity selector and action buttons */}</div>
);

export default ProductActions;
